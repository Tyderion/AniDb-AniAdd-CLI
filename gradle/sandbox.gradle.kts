import org.yaml.snakeyaml.Yaml
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

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
val libraryLinkName = "library"
val cacheName = "aniAdd.sqlite"
val additionalEnvKey = "ADDITIONAL_ENV"
val libraryRootKey = "LIBRARY_ROOT"
val sandboxConfig = ".run/sandbox.yaml"

/**
 * Args that would step around a gate in the settings file. exitOnBan is also a CLI option and the CLI
 * wins over config, so a run file could set it false and still pass a check that only reads sandbox.yaml;
 * db would point the sandbox at a different cache. Neither belongs in a run block.
 */
val forbiddenRunArgs = listOf("exit-on-ban", "exitOnBan", "db")
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
        if (block.lines().any { it.trim() == "bare" || it.trim() == "prunable" }) null
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

/**
 * How a worktree reaches a file in the container. Computed rather than assumed to be "..", so a worktree
 * nested a level deeper still gets a link that resolves, which is what the documentation already claimed.
 */
fun containerRelative(worktree: File, name: String): String =
    worktree.toPath().toAbsolutePath().normalize()
        .relativize(containerRoot().toPath().toAbsolutePath().normalize())
        .resolve(name)
        .toString()

fun targetWorktrees(): List<File> =
    if (project.hasProperty("all")) worktreePaths() else listOf(projectDir)

@Suppress("UNCHECKED_CAST")
fun loadYaml(file: File): Map<String, Any?> =
    file.inputStream().use { Yaml().load(it) as? Map<String, Any?> ?: emptyMap() }

tasks.register("envLink") {
    group = "setup"
    description = "Symlink everything named in the shared .env into this worktree: .env itself, alt.env, and the media library. -Pall does every worktree."
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
        // The library is the one link whose target is an absolute path outside the container, because it
        // is wherever this machine keeps its media. Naming it here is what lets the tracked configs say
        // ../library and stay free of anyone's home directory. It is the local equivalent of the bind
        // mount the Docker deployment uses for the same purpose.
        val libraryRoot = readEnvValue(sharedEnv, libraryRootKey)
        if (libraryRoot != null) {
            if (!File(libraryRoot).isDirectory) {
                throw GradleException("$libraryRootKey points at $libraryRoot, which is not a directory.")
            }
            link(root, libraryLinkName, libraryRoot, logger::lifecycle)
        }

        targetWorktrees().forEach { worktree ->
            link(worktree, sharedEnvName, containerRelative(worktree, sharedEnvName), logger::lifecycle)
            // The shared cache reaches each worktree by the same name it has in a plain clone, so
            // ../aniAdd.sqlite in a tracked config is correct either way and never writes outside a checkout.
            link(worktree, cacheName, containerRelative(worktree, cacheName), logger::lifecycle)
            if (additional == null) {
                unlinkIfSymlink(worktree, altEnvName, logger::lifecycle)
            } else {
                link(worktree, altEnvName, containerRelative(worktree, additional), logger::lifecycle)
            }
            if (libraryRoot == null) {
                unlinkIfSymlink(worktree, libraryLinkName, logger::lifecycle)
            } else {
                link(worktree, libraryLinkName, containerRelative(worktree, libraryLinkName), logger::lifecycle)
            }
        }
        if (libraryRoot == null) {
            logger.lifecycle("$libraryRootKey is not set in $sharedEnv, so scan-local.yaml and scan-inplace.yaml have nothing to point at.")
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
            worktreePaths().forEach { worktree ->
                link(worktree, sandboxLinkName, containerRelative(worktree, sandboxLinkName), logger::lifecycle)
            }
        }
        logger.lifecycle("Put real media in ${File(sandbox, "media")}, then run ./gradlew sandboxReset.")
    }
}

/**
 * Empties a directory inside the sandbox without ever following a link out of it.
 *
 * Kotlin's File.deleteRecursively walks through a directory symlink and deletes the target's contents,
 * so a sandbox folder linked at real media would be erased by a routine reset, silently and with no
 * prompt. Two defences: the resolved path must still be inside the sandbox, which catches a link at any
 * level rather than only on the leaf, and walkFileTree does not follow links, so a link found inside is
 * unlinked instead of chased. The directory itself is kept rather than deleted and recreated, so nothing
 * observes a moment where it is missing.
 */
fun clearInsideSandbox(sandbox: File, name: String, log: (String) -> Unit) {
    val dir = File(sandbox, name)
    if (!dir.exists()) {
        dir.mkdirs()
        return
    }
    val sandboxReal = sandbox.toPath().toRealPath()
    val dirReal = dir.toPath().toRealPath()
    if (!dirReal.startsWith(sandboxReal)) {
        throw GradleException(
            "Refusing to clear $dir: it resolves to $dirReal, outside the sandbox at $sandboxReal.\n" +
                "Something in that path is a symlink pointing elsewhere. Clearing it would delete whatever " +
                "it points at, which is how a reset would eat a real media folder."
        )
    }
    val root = dir.toPath()
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(current: Path, failure: IOException?): FileVisitResult {
            failure?.let { throw it }
            if (current != root) Files.delete(current)
            return FileVisitResult.CONTINUE
        }
    })
    log("  cleared $name")
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
            clearInsideSandbox(sandbox, name, logger::lifecycle)
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

        val libraryRoot = readEnvValue(sharedEnv, libraryRootKey)
        if (libraryRoot == null) {
            logger.lifecycle("$libraryRootKey is not set, so the local scan configs are inert. That is fine if you only use the sandbox.")
        } else if (!File(root, libraryLinkName).isDirectory) {
            findings += "$libraryRootKey is set but ${File(root, libraryLinkName)} does not resolve to a directory"
        }

        val linkNames = mutableMapOf(sharedEnvName to sharedEnvName, cacheName to cacheName)
        if (additional != null) linkNames[altEnvName] = additional
        if (libraryRoot != null) linkNames[libraryLinkName] = libraryLinkName
        if (sandboxRoot().isDirectory) linkNames[sandboxLinkName] = sandboxLinkName
        worktreePaths().forEach { worktree ->
            linkNames.mapValues { (_, target) -> containerRelative(worktree, target) }.forEach { (name, target) ->
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
        } else {
            logger.lifecycle("No sandbox yet. Run ./gradlew sandboxInit if you want one.")
        }

        // Re-read rather than trusted: the file is tracked, but it is also meant to be edited.
        val settingsFile = File(projectDir, sandboxConfig)
        val config = loadYaml(settingsFile)
        val mylistAdd = ((config["file"] as? Map<*, *>)?.get("mylist") as? Map<*, *>)?.get("add")
        val exitOnBan = (config["anidb"] as? Map<*, *>)?.get("exitOnBan")
        val markWatched = (config["kodi"] as? Map<*, *>)?.get("markWatched")
        if (mylistAdd != false) findings += "$sandboxConfig has file.mylist.add=$mylistAdd, expected false"
        if (exitOnBan != true) findings += "$sandboxConfig has anidb.exitOnBan=$exitOnBan, expected true"
        // Without this one a sandbox run against a real Kodi writes plays to the real MyList account.
        if (markWatched != false) findings += "$sandboxConfig has kodi.markWatched=$markWatched, expected false"

        // Checking only the settings file would be a gate with a door beside it: an entry point can send
        // the run somewhere else entirely, or override a gate through an arg that the CLI honours first.
        File(projectDir, ".run").listFiles { f: File ->
            f.name.startsWith("sandbox-") && f.name.endsWith(".yaml")
        }?.sortedBy { it.name }?.forEach { runFile ->
            val run = loadYaml(runFile)["run"] as? Map<*, *>
            if (run == null) {
                findings += "${runFile.name} has no run block, so it is not an entry point"
                return@forEach
            }
            val delegate = run["config"]?.toString()
            val resolved = delegate?.let { File(runFile.parentFile, it).canonicalFile }
            if (resolved != settingsFile.canonicalFile) {
                findings += "${runFile.name} delegates to $delegate, expected $sandboxConfig; " +
                    "its gates would not apply"
            }
            val runArgs = run["args"] as? Map<*, *> ?: emptyMap<Any, Any>()
            forbiddenRunArgs.filter { runArgs.containsKey(it) }.forEach { arg ->
                findings += "${runFile.name} sets '$arg' in args, which overrides the settings file"
            }
        }

        if (findings.isEmpty()) {
            logger.lifecycle("Setup looks complete: container $root, ${worktreePaths().size} worktree(s) linked.")
        } else {
            findings.forEach { logger.error("  - $it") }
            throw GradleException("${findings.size} setup problem(s) found. See docs/WorktreeSetup.md.")
        }
    }
}
