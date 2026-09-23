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
        if (block.lines().any { it.trim() == "bare" || it.trim().startsWith("prunable") }) null
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
        // Creating folders through a link would build the sandbox's shape inside whatever it points at.
        sandboxRootProblem(sandbox)?.let { throw GradleException("Refusing to set up the sandbox: $it") }
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
 * Why the sandbox root is not where it should be, or null if it is. The containment check in
 * clearInsideSandbox compares each folder with the sandbox's real path, so it cannot see a problem with the
 * root itself: link the whole sandbox at a real library and both sides resolve under the library, the check
 * passes, and a reset empties the library. So the root is pinned separately, before anything is cleared.
 *
 * A link above the sandbox is fine and deliberately allowed: it moves the whole container, sandbox
 * included, which is an ordinary setup (a code directory that is itself a link, say). What is refused is
 * the sandbox pointing somewhere other than its own container. A symlink shows up directly. A mount does
 * not, since toRealPath sees a mounted directory as real, so the filesystem is compared too: that catches
 * a network share or a different disk mounted there. A bind mount from the same filesystem is not caught.
 */
fun sandboxRootProblem(sandbox: File): String? {
    if (!sandbox.exists()) return null
    val path = sandbox.toPath()
    if (Files.isSymbolicLink(path)) {
        return "$sandbox is a symlink to ${Files.readSymbolicLink(path)}. The sandbox must be a real directory, " +
            "or a reset would empty whatever it points at."
    }
    val parentReal = sandbox.absoluteFile.parentFile.toPath().toRealPath()
    val expected = parentReal.resolve(sandbox.name)
    val actual = path.toRealPath()
    if (actual != expected) {
        return "$sandbox resolves to $actual, expected $expected."
    }
    if (Files.getFileStore(actual) != Files.getFileStore(parentReal)) {
        return "$sandbox is on a different filesystem from ${parentReal}, so something is mounted there. " +
            "A reset would empty whatever that is."
    }
    return null
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

/**
 * Everything that would let a sandbox run touch real folders, as a list of reasons. Empty means a sandbox
 * run cannot reach outside the sandbox. This is the invariant: a run configuration presented as a sandbox
 * run only ever reads and writes inside the sandbox, whatever the chain of files between the click and the
 * scan says.
 *
 * The chain has three links, and each is checked, because checking any subset leaves a door beside the
 * gate. The IntelliJ configuration decides which entry point runs; the entry point decides the input folder
 * and which settings apply; the settings decide where files are moved to and which gates are on.
 *
 * sandboxReset calls this before touching anything, and sandboxGuard exists to call it on its own, so a
 * sandbox configuration's before-launch step refuses to start rather than relying on someone having run
 * setupCheck recently.
 */
fun sandboxViolations(): List<String> {
    val findings = mutableListOf<String>()
    val sandbox = sandboxRoot().canonicalFile
    val runDir = File(projectDir, ".run")
    val settingsFile = File(projectDir, sandboxConfig).canonicalFile

    fun inside(base: File, value: Any?): File? = value?.toString()?.let { File(base, it).canonicalFile }
    fun isInsideSandbox(file: File?) = file != null && file.toPath().startsWith(sandbox.toPath())

    // Link 1: every configuration presented as a sandbox run must start a sandbox entry point, and must
    // keep a before-launch step that re-runs this check. Removing that step would remove the gate.
    runDir.listFiles { f: File -> f.name.endsWith(".run.xml") }?.sortedBy { it.name }?.forEach { xml ->
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val configuration = doc.getElementsByTagName("configuration").item(0) as? org.w3c.dom.Element
            ?: return@forEach
        val presentedAsSandbox = configuration.getAttribute("folderName") == "Sandbox" ||
            configuration.getAttribute("name").startsWith("Sandbox")
        if (!presentedAsSandbox) return@forEach

        val options = doc.getElementsByTagName("option")
        fun option(name: String): org.w3c.dom.Element? = (0 until options.length)
            .map { options.item(it) as org.w3c.dom.Element }
            .firstOrNull { it.getAttribute("name") == name }

        val parameters = option("PROGRAM_PARAMETERS")?.getAttribute("value").orEmpty()
        val configArg = Regex("""(?:--config=|-c\s+)(\S+)""").find(parameters)?.groupValues?.get(1)
        val workingDir = option("WORKING_DIRECTORY")?.getAttribute("value")
            ?.replace("\$PROJECT_DIR\$", projectDir.absolutePath)?.let(::File) ?: projectDir
        val target = configArg?.let { File(workingDir, it).canonicalFile }
        val isEntryPoint = target != null && target.parentFile == runDir.canonicalFile &&
            target.name.startsWith("sandbox-") && target.name.endsWith(".yaml")
        if (!isEntryPoint) {
            val where = when {
                configArg == null -> "no --config"
                target != null && target.path != File(projectDir, configArg).canonicalPath ->
                    "$configArg, which resolves to $target"
                else -> configArg
            }
            findings += "${xml.name} is a sandbox configuration but runs $where, not a .run/sandbox-*.yaml entry point"
        }
        val guarded = (0 until options.length)
            .map { options.item(it) as org.w3c.dom.Element }
            .filter { it.getAttribute("name") == "Gradle.BeforeRunTask" && it.getAttribute("enabled") == "true" }
            .any { task -> task.getAttribute("tasks").split(" ").any { it == "sandboxReset" || it == "sandboxGuard" } }
        if (!guarded) {
            findings += "${xml.name} has no sandboxReset or sandboxGuard before launch, so nothing checks it on Run"
        }
    }

    // Link 2: each entry point delegates to the sandbox settings, reads its input from the sandbox, and
    // does not override a gate through an arg the CLI honours ahead of the settings file.
    runDir.listFiles { f: File -> f.name.startsWith("sandbox-") && f.name.endsWith(".yaml") }
        ?.sortedBy { it.name }?.forEach { runFile ->
            val base = runFile.canonicalFile.parentFile
            val run = loadYaml(runFile)["run"] as? Map<*, *>
            if (run == null) {
                findings += "${runFile.name} has no run block, so it is not an entry point"
                return@forEach
            }
            if (inside(base, run["config"]) != settingsFile) {
                findings += "${runFile.name} delegates to ${run["config"]}, expected $sandboxConfig; " +
                    "its gates would not apply"
            }
            val runArgs = run["args"] as? Map<*, *> ?: emptyMap<Any, Any>()
            forbiddenRunArgs.filter { runArgs.containsKey(it) }.forEach { arg ->
                findings += "${runFile.name} sets '$arg' in args, which overrides the settings file"
            }
            runArgs["path"]?.let { path ->
                if (!isInsideSandbox(inside(base, path))) {
                    findings += "${runFile.name} reads input from $path, which is outside the sandbox at $sandbox"
                }
            }
        }

    // Link 3: the settings. The gates, and every folder a run writes to. The cache is deliberately shared
    // and the tag system is only read, so both are allowed to sit outside the sandbox.
    val config = loadYaml(settingsFile)
    val base = settingsFile.parentFile
    val file = config["file"] as? Map<*, *>
    val move = file?.get("move") as? Map<*, *>
    if ((file?.get("mylist") as? Map<*, *>)?.get("add") != false) {
        findings += "$sandboxConfig does not set file.mylist.add false"
    }
    if ((config["anidb"] as? Map<*, *>)?.get("exitOnBan") != true) {
        findings += "$sandboxConfig does not set anidb.exitOnBan true"
    }
    // Without this one a sandbox run against a real Kodi writes plays to the real MyList account.
    if ((config["kodi"] as? Map<*, *>)?.get("markWatched") != false) {
        findings += "$sandboxConfig does not set kodi.markWatched false"
    }
    val writeTargets = mutableListOf<Pair<String, Any?>>()
    listOf("unknown", "duplicates").forEach { kind ->
        writeTargets += "file.move.$kind.folder" to (move?.get(kind) as? Map<*, *>)?.get("folder")
    }
    val tagPaths = (config["tags"] as? Map<*, *>)?.get("paths") as? Map<*, *>
    listOf("movieFolders", "tvShowFolders").forEach { kind ->
        (tagPaths?.get(kind) as? List<*>)?.forEachIndexed { i, entry ->
            writeTargets += "tags.paths.$kind[$i].path" to (entry as? Map<*, *>)?.get("path")
        }
    }
    writeTargets.filter { it.second != null }.forEach { (key, value) ->
        if (!isInsideSandbox(inside(base, value))) {
            findings += "$sandboxConfig sends $key to $value, which is outside the sandbox at $sandbox"
        }
    }
    return findings
}

tasks.register("sandboxGuard") {
    group = "setup"
    description = "Refuse if any sandbox run configuration could read or write outside the sandbox. Used as a before-launch step."
    doLast {
        val violations = sandboxViolations()
        if (violations.isNotEmpty()) {
            violations.forEach { logger.error("  - $it") }
            throw GradleException("Refusing to start a sandbox run: ${violations.size} way(s) it could reach real folders.")
        }
        logger.lifecycle("Sandbox runs are confined to ${sandboxRoot().canonicalFile}.")
    }
}

tasks.register("sandboxReset") {
    group = "setup"
    description = "Refill the sandbox input from media/ and clear the output, unknown and duplicates folders."
    doLast {
        val sandbox = sandboxRoot()
        sandboxRootProblem(sandbox)?.let { throw GradleException("Refusing to reset: $it") }
        // Reset is the before-launch step of the sandbox configurations, so this is what makes the
        // confinement hold on every click rather than only when someone remembers setupCheck.
        sandboxViolations().takeIf { it.isNotEmpty() }?.let { violations ->
            violations.forEach { logger.error("  - $it") }
            throw GradleException("Refusing to reset: a sandbox run could reach real folders.")
        }
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

        sandboxRootProblem(sandboxRoot())?.let { findings += it }
        if (sandboxRoot().isDirectory) {
            sandboxDirs.forEach { name ->
                if (!File(sandboxRoot(), name).isDirectory) findings += "missing sandbox directory $name"
            }
        } else {
            logger.lifecycle("No sandbox yet. Run ./gradlew sandboxInit if you want one.")
        }

        findings += sandboxViolations()

        if (findings.isEmpty()) {
            logger.lifecycle("Setup looks complete: container $root, ${worktreePaths().size} worktree(s) linked.")
        } else {
            findings.forEach { logger.error("  - $it") }
            throw GradleException("${findings.size} setup problem(s) found. See docs/WorktreeSetup.md.")
        }
    }
}
