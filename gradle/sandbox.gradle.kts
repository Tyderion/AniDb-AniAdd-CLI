import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Paths

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.yaml:snakeyaml:2.0") }
}

// Local development setup for the .bare + worktrees layout described in docs/WorktreeSetup.md.
// Everything shared between worktrees (credentials, the AniDB cache, the sandbox) lives in the
// container directory that holds .bare, and each worktree reaches it through relative symlinks.
//
// The config files are tracked, in .run/. Nothing here generates one: these tasks only create
// directories and the symlinks that make ../sandbox mean the same thing in every checkout.

val sharedEnvName = ".env"
val altEnvName = "alt.env"
val sandboxLinkName = "sandbox"
val cacheName = "aniAdd.sqlite"
val additionalEnvKey = "ADDITIONAL_ENV"
val sandboxConfig = ".run/sandbox.yaml"
val sandboxDirs = listOf("media", "input", "unknown", "duplicates", "output/movies", "output/series")

/**
 * The container is the parent of git's common directory: shared by every worktree, and reported by git
 * itself rather than guessed from `..`, so it is still correct if a worktree is nested deeper. A plain
 * clone has a normal .git and no container, and gets null.
 */
fun containerRootOrNull(): File? {
    val commonDir = git("rev-parse", "--path-format=absolute", "--git-common-dir")
    val bare = git("--git-dir=$commonDir", "config", "--get", "core.bare", failOnError = false)
    return if (bare == "true") File(commonDir).parentFile else null
}

fun containerRoot(): File = containerRootOrNull() ?: throw GradleException(
    "This repository is a plain clone, not the worktree layout, so there is nothing to share between\n" +
        "checkouts. See docs/WorktreeSetup.md for how to convert. sandboxInit and sandboxReset still work\n" +
        "here; they build the sandbox inside the checkout instead."
)

/** In the worktree layout the sandbox is shared; in a plain clone it simply lives in the checkout. */
fun sandboxRoot(): File =
    containerRootOrNull()?.let { File(it, sandboxLinkName) } ?: File(projectDir, sandboxLinkName)

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
    value.contains("..") -> "must not traverse directories: '$value'"
    !File(containerRoot(), value).isFile -> "names '$value', which does not exist in ${containerRoot()}"
    else -> null
}

/**
 * Replaces an existing symlink, but never a real file or directory: it could be the only copy of someone's
 * credentials or test media, and deleting it to install a link is not a trade this task gets to make.
 */
fun link(parent: File, linkName: String, target: String, log: (String) -> Unit) {
    val linkPath = parent.toPath().resolve(linkName)
    if (Files.exists(linkPath, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(linkPath)) {
        throw GradleException(
            "$linkPath already exists and is not a symlink. Move its contents to ${containerRoot()} and run " +
                "this task again; refusing to delete it."
        )
    }
    Files.deleteIfExists(linkPath)
    Files.createSymbolicLink(linkPath, Paths.get(target))
    log("  linked ${parent.name}/$linkName -> $target")
}

fun unlinkIfSymlink(parent: File, linkName: String, log: (String) -> Unit) {
    val linkPath = parent.toPath().resolve(linkName)
    if (Files.isSymbolicLink(linkPath)) {
        Files.delete(linkPath)
        log("  removed stale ${parent.name}/$linkName")
    }
}

fun targetWorktrees(): List<File> =
    if (project.hasProperty("all")) worktreePaths() else listOf(projectDir)

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
    description = "Create the sandbox directories and, in the worktree layout, link the shared sandbox into every worktree."
    doLast {
        val sandbox = sandboxRoot()
        sandboxDirs.forEach { File(sandbox, it).mkdirs() }
        logger.lifecycle("sandbox tree ready at $sandbox")

        val container = containerRootOrNull()
        if (container == null) {
            logger.lifecycle("Plain clone, so the sandbox lives in the checkout and needs no links.")
        } else {
            // The sandbox borrows the shared cache rather than starting an empty one, so a file identified
            // in a real run is not looked up again here. One tracked path covers both layouts.
            link(sandbox, cacheName, "../$cacheName", logger::lifecycle)
            worktreePaths().forEach { worktree ->
                link(worktree, sandboxLinkName, "../$sandboxLinkName", logger::lifecycle)
            }
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
        if (!File(root, cacheName).isFile) findings += "missing ${File(root, cacheName)} (the shared AniDB cache)"

        val additional = readEnvValue(sharedEnv, additionalEnvKey)
        if (additional == null) {
            findings += "$additionalEnvKey is not set in $sharedEnv, so no worktree gets an $altEnvName"
        } else {
            additionalEnvProblem(additional)?.let { findings += "$additionalEnvKey $it" }
        }

        val expected = mutableMapOf(sharedEnvName to "../$sharedEnvName")
        if (additional != null) expected[altEnvName] = "../$additional"
        if (sandboxRoot().isDirectory) expected[sandboxLinkName] = "../$sandboxLinkName"
        worktreePaths().forEach { worktree ->
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

        if (sandboxRoot().isDirectory) {
            sandboxDirs.forEach { name ->
                if (!File(sandboxRoot(), name).isDirectory) findings += "missing sandbox directory $name"
            }
            if (!Files.isSymbolicLink(File(sandboxRoot(), cacheName).toPath())) {
                findings += "${File(sandboxRoot(), cacheName)} does not link to the shared cache, " +
                    "so the sandbox would start an empty one and look every file up again"
            }
        } else {
            logger.lifecycle("No sandbox yet. Run ./gradlew sandboxInit if you want one.")
        }

        // Re-read rather than trusted: the file is tracked, but it is also meant to be edited.
        val config = loadYaml(File(projectDir, sandboxConfig))
        val mylistAdd = ((config["file"] as? Map<*, *>)?.get("mylist") as? Map<*, *>)?.get("add")
        val exitOnBan = (config["anidb"] as? Map<*, *>)?.get("exitOnBan")
        if (mylistAdd != false) findings += "$sandboxConfig has file.mylist.add=$mylistAdd, expected false"
        if (exitOnBan != true) findings += "$sandboxConfig has anidb.exitOnBan=$exitOnBan, expected true"

        if (findings.isEmpty()) {
            logger.lifecycle("Setup looks complete: container $root, ${worktreePaths().size} worktree(s) linked.")
        } else {
            findings.forEach { logger.error("  - $it") }
            throw GradleException("${findings.size} setup problem(s) found. See docs/WorktreeSetup.md.")
        }
    }
}
