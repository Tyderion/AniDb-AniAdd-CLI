import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.yaml:snakeyaml:2.0") }
}

// Local development setup for the .bare + worktrees layout described in docs/WorktreeSetup.md.
// Everything shared between worktrees (credentials, the AniDB cache, the sandbox) lives in the
// container directory that holds .bare, and each worktree reaches it through relative symlinks.

val sharedEnvName = ".env"
val altEnvName = "alt.env"
val sandboxConfigName = "sandbox.yaml"
val additionalEnvKey = "ADDITIONAL_ENV"
val sandboxDirs = listOf("media", "input", "unknown", "duplicates", "output/movies", "output/series")

/**
 * The container is the parent of git's common directory: shared by every worktree, and reported by
 * git itself rather than guessed from `..`, so it is still correct if a worktree is nested deeper.
 * A plain clone has a normal .git and no container, and gets a clear refusal instead of symlinks
 * scattered into whatever directory happens to sit above the checkout.
 */
fun containerRoot(): File {
    val commonDir = git("rev-parse", "--path-format=absolute", "--git-common-dir")
    val bare = git("--git-dir=$commonDir", "config", "--get", "core.bare", failOnError = false)
    if (bare != "true") {
        throw GradleException(
            "This repository is a plain clone, not the worktree layout: git's common directory is\n" +
                "  $commonDir\n" +
                "which is not a bare repository. See docs/WorktreeSetup.md for how to convert, or skip\n" +
                "these tasks and keep .env and the cache inside the checkout."
        )
    }
    return File(commonDir).parentFile
}

fun sandboxRoot(): File = File(containerRoot(), "sandbox")

/**
 * Plain ProcessBuilder rather than providers.exec: these all run inside task actions, where a direct
 * call is both simpler and free of the value-source restrictions the provider API imposes.
 */
fun git(vararg args: String, failOnError: Boolean = true): String {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(projectDir)
        .redirectErrorStream(false)
        .start()
    val out = process.inputStream.bufferedReader().readText().trim()
    val err = process.errorStream.bufferedReader().readText().trim()
    val code = process.waitFor()
    if (code != 0 && failOnError) {
        throw GradleException("git ${args.joinToString(" ")} failed with exit code $code: $err")
    }
    return out
}

/** Every linked worktree, bare entry excluded: it has no working tree to link anything into. */
fun worktreePaths(): List<File> {
    val blocks = git("worktree", "list", "--porcelain").split("\n\n")
    return blocks.mapNotNull { block ->
        if (block.lines().any { it.trim() == "bare" }) null
        else block.lines().firstOrNull { it.startsWith("worktree ") }?.removePrefix("worktree ")?.let(::File)
    }
}

fun readEnvValue(envFile: File, key: String): String? {
    if (!envFile.isFile) return null
    return envFile.readLines()
        .map { it.trim() }
        .filterNot { it.isEmpty() || it.startsWith("#") }
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
        .lastOrNull { it.first == key }
        ?.second
        ?.trim('"', '\'')
        ?.ifBlank { null }
}

/**
 * ADDITIONAL_ENV names a file next to .env, never a path: anything with a separator would point the
 * symlink outside the container, away from the rest of the shared state.
 */
fun additionalEnvProblem(value: String): String? = when {
    value.contains('/') || value.contains('\\') -> "must be a bare filename, not a path: '$value'"
    value == ".." || value.contains("..") -> "must not traverse directories: '$value'"
    !File(containerRoot(), value).isFile -> "names '$value', which does not exist in ${containerRoot()}"
    else -> null
}

/**
 * Replaces an existing symlink, but never a real file: that file could be the only copy of someone's
 * credentials, and silently deleting it to install a link is not a trade this task gets to make.
 */
fun link(worktree: File, linkName: String, target: String, log: (String) -> Unit) {
    val linkPath = worktree.toPath().resolve(linkName)
    if (Files.exists(linkPath, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(linkPath)) {
        throw GradleException(
            "$linkPath is a regular file, not a symlink. Move it to ${containerRoot()} and run this task " +
                "again; refusing to delete it."
        )
    }
    Files.deleteIfExists(linkPath)
    Files.createSymbolicLink(linkPath, Paths.get(target))
    log("  linked ${worktree.name}/$linkName -> $target")
}

fun unlinkIfSymlink(worktree: File, linkName: String, log: (String) -> Unit) {
    val linkPath = worktree.toPath().resolve(linkName)
    if (Files.isSymbolicLink(linkPath)) {
        Files.delete(linkPath)
        log("  removed stale ${worktree.name}/$linkName")
    }
}

fun targetWorktrees(): List<File> =
    if (project.hasProperty("all")) worktreePaths() else listOf(projectDir)

@Suppress("UNCHECKED_CAST")
fun deepMerge(base: Map<String, Any?>, override: Map<String, Any?>): Map<String, Any?> {
    val merged = LinkedHashMap<String, Any?>(base)
    override.forEach { (key, value) ->
        val existing = merged[key]
        merged[key] = if (existing is Map<*, *> && value is Map<*, *>) {
            deepMerge(existing as Map<String, Any?>, value as Map<String, Any?>)
        } else {
            value
        }
    }
    return merged
}

fun substituteTokens(node: Any?, tokens: Map<String, String>): Any? = when (node) {
    is String -> tokens.entries.fold(node) { acc, (k, v) -> acc.replace(k, v) }
    is List<*> -> node.map { substituteTokens(it, tokens) }
    is Map<*, *> -> node.entries.associate { (k, v) -> k as String to substituteTokens(v, tokens) }
    else -> node
}

/** Multi-line values are emitted as block literals so the generated config stays readable and editable. */
fun dumpYaml(data: Map<String, Any?>): String {
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
    }
    val representer = object : Representer(options) {
        override fun representScalar(tag: Tag, value: String, style: DumperOptions.ScalarStyle?) =
            super.representScalar(
                tag,
                value,
                if (value.contains("\n")) DumperOptions.ScalarStyle.LITERAL else style
            )
    }
    return Yaml(representer, options).dump(data)
}

@Suppress("UNCHECKED_CAST")
fun loadYaml(file: File): Map<String, Any?> =
    file.inputStream().use { Yaml().load(it) as? Map<String, Any?> ?: emptyMap() }

tasks.register("envLink") {
    group = "setup"
    description = "Symlink the shared .env (and the ADDITIONAL_ENV file as alt.env) into this worktree. -Pall does every worktree."
    doLast {
        val root = containerRoot()
        val sharedEnv = File(root, sharedEnvName)
        if (!sharedEnv.isFile) {
            throw GradleException("No $sharedEnvName in $root. Create it there first; it is the shared one.")
        }
        val additional = readEnvValue(sharedEnv, additionalEnvKey)
        additional?.let { value ->
            additionalEnvProblem(value)?.let { throw GradleException("$additionalEnvKey $it") }
        }
        targetWorktrees().forEach { worktree ->
            link(worktree, sharedEnvName, "../$sharedEnvName", logger::lifecycle)
            if (additional == null) {
                unlinkIfSymlink(worktree, altEnvName, logger::lifecycle)
            } else {
                link(worktree, altEnvName, "../$additional", logger::lifecycle)
            }
        }
        if (additional == null) {
            logger.lifecycle("$additionalEnvKey is not set in $sharedEnv, so no $altEnvName was created.")
        }
    }
}

tasks.register("sandboxInit") {
    group = "setup"
    description = "Create the shared sandbox tree and generate its config, then link it into every worktree."
    doLast {
        val root = containerRoot()
        val sandbox = sandboxRoot()
        sandboxDirs.forEach { File(sandbox, it).mkdirs() }
        logger.lifecycle("sandbox tree ready at $sandbox")

        val generated = File(sandbox, sandboxConfigName)
        if (generated.isFile && !project.hasProperty("force")) {
            logger.lifecycle("$generated already exists, leaving it alone. Use -Pforce to regenerate.")
        } else {
            val base = loadYaml(File(projectDir, ".run/scan-local.yaml"))
            val overrides = loadYaml(File(projectDir, "gradle/sandbox-overrides.yaml"))
            val tokens = mapOf("@ROOT@" to root.absolutePath, "@SANDBOX@" to sandbox.absolutePath)
            @Suppress("UNCHECKED_CAST")
            val merged = substituteTokens(deepMerge(base, overrides), tokens) as Map<String, Any?>
            generated.writeText(
                "# Generated by ./gradlew sandboxInit. Edit freely; it is never regenerated without -Pforce,\n" +
                    "# and it is not tracked by git.\n" + dumpYaml(merged)
            )
            logger.lifecycle("wrote $generated")
        }

        worktreePaths().forEach { worktree ->
            link(worktree, sandboxConfigName, "../sandbox/$sandboxConfigName", logger::lifecycle)
        }
        logger.lifecycle("Put real media in ${File(sandbox, "media")}, then run ./gradlew sandboxReset.")
    }
}

tasks.register("sandboxReset") {
    group = "setup"
    description = "Refill the sandbox input from media/ and clear the output, unknown and duplicates folders."
    doLast {
        val sandbox = sandboxRoot()
        val media = File(sandbox, "media")
        if (!media.isDirectory) {
            throw GradleException("No $media. Run ./gradlew sandboxInit first.")
        }
        listOf("input", "unknown", "duplicates", "output/movies", "output/series").forEach { name ->
            val dir = File(sandbox, name)
            dir.deleteRecursively()
            dir.mkdirs()
        }
        val copied = media.walkTopDown().filter { it.isFile }.map { source ->
            val target = File(sandbox, "input").resolve(source.relativeTo(media).path)
            target.parentFile.mkdirs()
            source.copyTo(target, overwrite = true)
        }.count()
        logger.lifecycle("sandbox reset: $copied file(s) copied from media/ into input/")
        if (copied == 0) {
            logger.warn("media/ is empty, so a run would have nothing to process. Copy real files in first.")
        }
    }
}

tasks.register("setupCheck") {
    group = "setup"
    description = "Report anything missing or unsafe in the shared setup. Fails if it finds a problem."
    doLast {
        val root = containerRoot()
        val findings = mutableListOf<String>()
        val sharedEnv = File(root, sharedEnvName)

        if (!sharedEnv.isFile) findings += "missing $sharedEnv (the shared credentials file)"
        if (!File(root, "aniAdd.sqlite").isFile) {
            findings += "missing ${File(root, "aniAdd.sqlite")} (the shared AniDB cache)"
        }

        val additional = readEnvValue(sharedEnv, additionalEnvKey)
        if (additional == null) {
            findings += "$additionalEnvKey is not set in $sharedEnv, so no worktree gets an $altEnvName"
        } else {
            additionalEnvProblem(additional)?.let { findings += "$additionalEnvKey $it" }
        }

        worktreePaths().forEach { worktree ->
            val expected = mutableMapOf(sharedEnvName to "../$sharedEnvName")
            if (additional != null) expected[altEnvName] = "../$additional"
            if (File(sandboxRoot(), sandboxConfigName).isFile) {
                expected[sandboxConfigName] = "../sandbox/$sandboxConfigName"
            }
            expected.forEach { (name, target) ->
                val path = worktree.toPath().resolve(name)
                when {
                    !Files.isSymbolicLink(path) -> findings += "${worktree.name}/$name is not a symlink"
                    Files.readSymbolicLink(path).toString() != target ->
                        findings += "${worktree.name}/$name points at ${Files.readSymbolicLink(path)}, expected $target"
                    !Files.exists(path) -> findings += "${worktree.name}/$name is a broken link"
                }
            }
        }

        val generated = File(sandboxRoot(), sandboxConfigName)
        if (generated.isFile) {
            sandboxDirs.forEach { name ->
                if (!File(sandboxRoot(), name).isDirectory) findings += "missing sandbox directory $name"
            }
            // The gates are re-read rather than trusted, because the generated file is meant to be hand-edited.
            val config = loadYaml(generated)
            val mylistAdd = ((config["file"] as? Map<*, *>)?.get("mylist") as? Map<*, *>)?.get("add")
            val exitOnBan = (config["anidb"] as? Map<*, *>)?.get("exitOnBan")
            val cacheDb = ((config["anidb"] as? Map<*, *>)?.get("cache") as? Map<*, *>)?.get("db")
            if (mylistAdd != false) findings += "$generated has file.mylist.add=$mylistAdd, expected false"
            if (exitOnBan != true) findings += "$generated has anidb.exitOnBan=$exitOnBan, expected true"
            if (cacheDb != File(root, "aniAdd.sqlite").absolutePath) {
                findings += "$generated points at cache $cacheDb, expected the shared ${File(root, "aniAdd.sqlite")}"
            }
        } else {
            logger.lifecycle("No sandbox config yet. Run ./gradlew sandboxInit if you want one.")
        }

        if (findings.isEmpty()) {
            logger.lifecycle("Setup looks complete: container $root, ${worktreePaths().size} worktree(s) linked.")
        } else {
            findings.forEach { logger.error("  - $it") }
            throw GradleException("${findings.size} setup problem(s) found. See docs/WorktreeSetup.md.")
        }
    }
}
