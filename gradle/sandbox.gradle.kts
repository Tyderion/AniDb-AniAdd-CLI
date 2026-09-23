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

/** Links name to a container file, or removes a stale link when the .env leaves it unset. */
fun syncLink(worktree: File, name: String, target: String?, log: (String) -> Unit) =
    if (target == null) unlinkIfSymlink(worktree, name, log)
    else link(worktree, name, containerRelative(worktree, target), log)

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
            // A relative value would be checked against the Gradle daemon's working directory but resolved
            // against the container once it is a link, so the two would disagree.
            if (!File(libraryRoot).isAbsolute) {
                throw GradleException("$libraryRootKey must be an absolute path, got '$libraryRoot'.")
            }
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
            syncLink(worktree, altEnvName, additional, logger::lifecycle)
            syncLink(worktree, libraryLinkName, libraryRoot?.let { libraryLinkName }, logger::lifecycle)
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
        sandboxDirs.forEach { name ->
            linkBetween(sandbox, name)?.let { link ->
                throw GradleException("Refusing to set up the sandbox: $link is a symlink, so creating $name would write wherever it points.")
            }
            File(sandbox, name).mkdirs()
        }
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
 * The first symlink between the sandbox root and a folder inside it, or null. File.mkdirs retries through
 * the canonical path when a parent is missing, so a linked sandbox/output would make it create
 * output/movies wherever the link points, even when the link is dangling.
 */
fun linkBetween(sandbox: File, name: String): Path? {
    var current = sandbox.toPath()
    for (part in Paths.get(name)) {
        current = current.resolve(part)
        if (Files.isSymbolicLink(current)) return current
    }
    return null
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
    val path = sandbox.toPath()
    // Before the exists check, which follows links: a link to a folder that does not exist yet would
    // otherwise pass, and sandboxInit would then create the whole sandbox inside wherever it points.
    if (Files.isSymbolicLink(path)) {
        return "$sandbox is a symlink to ${Files.readSymbolicLink(path)}. The sandbox must be a real directory, " +
            "or a reset would empty whatever it points at."
    }
    if (!sandbox.exists()) return null
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
    linkBetween(sandbox, name)?.takeIf { !dir.exists() }?.let { link ->
        throw GradleException("Refusing to recreate $dir: $link is a symlink, so it would be created wherever that points.")
    }
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
    // The root first, and alone. Every check below measures "inside" against the sandbox's real path, so a
    // sandbox replaced by a link to the library would move that yardstick onto the library and pass the lot.
    // Reporting the paths as well would only bury the one finding that explains them.
    val root = sandboxRoot()
    sandboxRootProblem(root)?.let { return listOf(it) }
    val findings = mutableListOf<String>()
    val sandbox = root.canonicalFile
    val runDir = File(projectDir, ".run")
    val settingsFile = File(projectDir, sandboxConfig).canonicalFile

    // The app normalises ".." lexically before the filesystem sees the path, so a ".." after a link means
    // something different to it than to canonicalFile. Resolving in the app's order judges the same path.
    fun inside(base: File, value: Any?): File? =
        value?.toString()?.let { base.toPath().resolve(it).normalize().toFile().canonicalFile }
    fun isInsideSandbox(file: File?) = file != null && file.toPath().startsWith(sandbox.toPath())

    // Link 1: every configuration presented as a sandbox run must start a sandbox entry point, and must
    // keep a before-launch step that re-runs this check. Removing that step would remove the gate.
    // Tracked .run files are not the only place IntelliJ keeps configurations: a copy made with "Copy
    // Configuration" lives in .idea/workspace.xml, and older projects use .idea/runConfigurations. A copy
    // pointed at real folders would keep the before-launch guard yet be invisible to it, so all three are read.
    val stores = (runDir.listFiles { f: File -> f.name.endsWith(".run.xml") }.orEmpty().toList() +
        listOfNotNull(File(projectDir, ".idea/workspace.xml").takeIf { it.isFile }) +
        File(projectDir, ".idea/runConfigurations").listFiles { f: File -> f.name.endsWith(".xml") }.orEmpty())
        .sortedBy { it.path }
    stores.forEach { xml ->
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val configurations = doc.getElementsByTagName("configuration")
            .let { list -> (0 until list.length).map { list.item(it) as org.w3c.dom.Element } }
            // Templates describe defaults for new configurations; they never run themselves.
            .filter { it.getAttribute("default") != "true" }
        configurations.forEach { configuration ->
            val presentedAsSandbox = configuration.getAttribute("folderName") == "Sandbox" ||
                configuration.getAttribute("name").startsWith("Sandbox")
            if (!presentedAsSandbox) return@forEach
            val label = if (xml.name.endsWith(".run.xml")) xml.name else "${xml.name} (${configuration.getAttribute("name")})"

            // Scoped to this configuration: a store like workspace.xml holds many.
            val options = configuration.getElementsByTagName("option")
                .let { list -> (0 until list.length).map { list.item(it) as org.w3c.dom.Element } }
            fun option(name: String) = options.firstOrNull { it.getAttribute("name") == name }

            val parameters = option("PROGRAM_PARAMETERS")?.getAttribute("value").orEmpty()
            // Exactly `run --config=<entry point>` or `run -c <entry point>`. Reading only the --config value let
            // a configuration run `anidb scan --config=<sandbox file> <any folder>`: the entry point would be used
            // as the settings file, sandbox.yaml and its gates would never apply, and the folder was never checked.
            val tokens = parameters.trim().split(Regex("\\s+"))
            val configArg = when {
                tokens.size == 2 && tokens[0] == "run" && tokens[1].startsWith("--config=") -> tokens[1].removePrefix("--config=")
                tokens.size == 3 && tokens[0] == "run" && tokens[1] == "-c" -> tokens[2]
                else -> null
            }
            val workingDir = option("WORKING_DIRECTORY")?.getAttribute("value")
                ?.replace("\$PROJECT_DIR\$", projectDir.absolutePath)?.let(::File) ?: projectDir
            val target = configArg?.let { File(workingDir, it).canonicalFile }
            val isEntryPoint = target != null && target.parentFile == runDir.canonicalFile &&
                target.name.startsWith("sandbox-") && target.name.endsWith(".yaml")
            if (!isEntryPoint) {
                val where = configArg?.let { "$it (resolves to $target)" }
                    ?: "'$parameters', which is not the form 'run --config=<entry point>'"
                findings += "$label is a sandbox configuration but runs $where, not a .run/sandbox-*.yaml entry point"
            }
            // The step only guards if it runs the guard here and as written: extra Gradle arguments such as
            // "-x sandboxGuard" or "--dry-run" skip it, and another project path checks someone else's configs.
            val guarded = options
                .filter { it.getAttribute("name") == "Gradle.BeforeRunTask" && it.getAttribute("enabled") == "true" }
                .filter { it.getAttribute("scriptParameters").isBlank() }
                .filter { it.getAttribute("externalProjectPath") == "\$PROJECT_DIR\$" }
                .any { task -> task.getAttribute("tasks").split(" ").any { it == "sandboxReset" || it == "sandboxGuard" } }
            if (!guarded) {
                findings += "$label has no plain sandboxReset or sandboxGuard before launch, so nothing checks it on Run"
            }
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
    // The static checks here cannot see a destination the tag system computes at run time; this makes the app
    // itself refuse any move outside the sandbox, so it has to be present and has to mean the sandbox.
    val confineTo = inside(base, move?.get("confineTo"))
    if (confineTo != sandbox) {
        findings += "$sandboxConfig sets file.move.confineTo to ${move?.get("confineTo")}, expected the sandbox ($sandbox)"
    }
    // A library scan triggers a scan and, if configured, a clean on the real Kodi library.
    val libraryScan = ((config["kodi"] as? Map<*, *>)?.get("libraryScan") as? Map<*, *>)?.get("enabled")
    if (libraryScan != false) {
        findings += "$sandboxConfig does not set kodi.libraryScan.enabled false"
    }
    val writeTargets = mutableListOf<Pair<String, Any?>>()
    // FOLDER move mode sends every file here.
    writeTargets += "file.move.folder" to move?.get("folder")
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

/**
 * The guard checks every sandbox configuration, not only the one being launched, so one wrong file blocks
 * them all. The message has to say that, and has to carry the findings itself: IntelliJ shows the exception
 * text prominently and the log lines above it not at all, and "a sandbox run could reach real folders" on a
 * configuration you never touched sends you looking in the wrong file.
 */
fun sandboxBlocked(violations: List<String>): GradleException {
    return GradleException(buildString {
        append("All sandbox runs are blocked, including this one, until ")
        append(if (violations.size == 1) "this is fixed" else "these ${violations.size} problems are fixed")
        append(". The check covers every sandbox configuration, so the problem may be in a file you did not run:\n")
        violations.forEach { append("  - ").append(it).append('\n') }
        append("Run ./gradlew sandboxGuard to check again once fixed.")
    })
}

tasks.register("sandboxGuard") {
    group = "setup"
    description = "Refuse if any sandbox run configuration could read or write outside the sandbox. Used as a before-launch step."
    doLast {
        val violations = sandboxViolations()
        if (violations.isNotEmpty()) {
            throw sandboxBlocked(violations)
        }
        logger.lifecycle("Sandbox runs are confined to ${sandboxRoot().canonicalFile}.")
    }
}

tasks.register("sandboxReset") {
    group = "setup"
    description = "Refill the sandbox input from media/ and clear the output, unknown and duplicates folders."
    // Reset is the before-launch step of the sandbox configurations, so running the guard first is what
    // makes the confinement hold on every click rather than only when someone remembers setupCheck.
    dependsOn("sandboxGuard")
    doLast {
        val sandbox = sandboxRoot()
        // The guard has already checked this, but it can be skipped with -x sandboxGuard, and this is the one
        // check that stands directly between the clear below and a real library.
        sandboxRootProblem(sandbox)?.let { throw GradleException("Refusing to reset: $it") }
        val media = File(sandbox, "media")
        if (!media.isDirectory) {
            throw GradleException("No $media. Run ./gradlew sandboxInit first.")
        }
        // Everything but media/, which holds the files being tested and is never touched.
        (sandboxDirs - "media").forEach { name ->
            clearInsideSandbox(sandbox, name, logger::lifecycle)
        }
        media.copyRecursively(File(sandbox, "input"))
        val copied = media.walkTopDown().count { it.isFile }
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
        // Each of these asks git; computed once so the whole check reasons about one answer.
        val sandbox = sandboxRoot()
        val worktrees = worktreePaths()
        val findings = mutableListOf<String>()
        val sharedEnv = File(root, sharedEnvName)

        if (!sharedEnv.isFile) findings += "missing $sharedEnv (the shared credentials file)"
        // A fresh container has no cache yet: the first run creates it through the worktree link. That is a
        // normal state, not a fault, so it is reported rather than failing the check.
        val cacheExists = File(root, cacheName).isFile
        if (!cacheExists) {
            logger.lifecycle("No shared AniDB cache yet at ${File(root, cacheName)}; the first run will create it.")
        }

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
        if (sandbox.isDirectory) linkNames[sandboxLinkName] = sandboxLinkName
        worktrees.forEach { worktree ->
            linkNames.mapValues { (_, target) -> containerRelative(worktree, target) }.forEach { (name, target) ->
                val path = worktree.toPath().resolve(name)
                when {
                    !Files.isSymbolicLink(path) -> findings += "${worktree.name}/$name is not a symlink"
                    Files.readSymbolicLink(path).toString() != target ->
                        findings += "${worktree.name}/$name points at ${Files.readSymbolicLink(path)}, expected $target"
                    // Dangling on purpose until the first run, see above.
                    !Files.exists(path) && !(name == cacheName && !cacheExists) ->
                        findings += "${worktree.name}/$name is a broken link"
                }
            }
        }

        // A bad root is reported by sandboxViolations below, first and on its own.
        if (sandbox.isDirectory) {
            sandboxDirs.forEach { name ->
                if (!File(sandbox, name).isDirectory) findings += "missing sandbox directory $name"
            }
        } else {
            logger.lifecycle("No sandbox yet. Run ./gradlew sandboxInit if you want one.")
        }

        findings += sandboxViolations()

        if (findings.isEmpty()) {
            logger.lifecycle("Setup looks complete: container $root, ${worktrees.size} worktree(s) linked.")
        } else {
            findings.forEach { logger.error("  - $it") }
            throw GradleException("${findings.size} setup problem(s) found. See docs/WorktreeSetup.md.")
        }
    }
}
