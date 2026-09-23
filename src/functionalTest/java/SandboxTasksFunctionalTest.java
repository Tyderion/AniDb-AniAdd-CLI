import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Runs the setup tasks in gradle/sandbox.gradle.kts against real throwaway worktree containers and inspects
 * the filesystem afterwards.
 *
 * These exist because the script's job is mostly to refuse: to not delete through a link, to not start a
 * sandbox run that could reach real folders, to not replace someone's credentials. That can only be shown
 * by building the dangerous situation and checking what survived, and the ordering matters as much as the
 * checks do, since a guard that runs after the clear protects nothing. Every case here started life as a
 * probe run by hand while the script was written.
 *
 * Each fixture copies the real script and the real tracked configs from the repository, so the configs
 * being guarded are the ones that ship.
 */
public class SandboxTasksFunctionalTest {

    private static final Path REPO = Path.of(System.getProperty("aniadd.repo"));

    @TempDir
    Path tempDir;

    /** Resolved once: on macOS the temp root is behind a link, and the script compares real paths. */
    Path root;
    Path container;
    Path worktree;
    Path sandbox;

    @BeforeEach
    void buildContainer() throws IOException, InterruptedException {
        root = tempDir.toRealPath();
        container = root.resolve("container");
        worktree = container.resolve("wt");
        sandbox = container.resolve("sandbox");

        Path seed = root.resolve("seed");
        writeFixtureProject(seed);
        git(seed, "init", "-q", "-b", "main");
        git(seed, "add", "-A");
        git(seed, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-q", "-m", "fixture");

        Files.createDirectories(container);
        git(root, "clone", "-q", "--bare", seed.toString(), container.resolve(".bare").toString());
        Files.writeString(container.resolve(".git"), "gitdir: ./.bare\n");
        git(container, "worktree", "add", "-q", worktree.toString(), "main");
        Files.writeString(container.resolve(".env"), "ANIDB_USERNAME=someone\n");
    }

    // --- sandboxReset: what it deletes, and what it refuses to ---------------------------------------------

    @Test
    public void resetClearsTheOutputAndRefillsInputFromMedia() throws Exception {
        run("sandboxInit");
        write(sandbox.resolve("media/episode.mkv"), "media");
        write(sandbox.resolve("input/stale.mkv"), "stale");
        write(sandbox.resolve("output/series/Show/e01.mkv"), "moved");

        run("sandboxReset");

        assertThat(Files.readString(sandbox.resolve("input/episode.mkv")), is("media"));
        assertThat(Files.exists(sandbox.resolve("input/stale.mkv")), is(false));
        assertThat(isEmpty(sandbox.resolve("output/series")), is(true));
        assertThat("media is never touched", Files.readString(sandbox.resolve("media/episode.mkv")), is("media"));
    }

    @Test
    public void resetRefusesWhenTheSandboxItselfIsALink() throws Exception {
        Path library = libraryShapedFolder(root.resolve("library"));
        Files.createSymbolicLink(sandbox, container.relativize(library));

        BuildResult result = fail("sandboxReset");

        assertThat(result.getOutput(), containsString("is a symlink"));
        assertThat("the linked library survived", Files.readString(library.resolve("input/keep.mkv")), is("precious"));
    }

    /**
     * Caught by the guard rather than the clear: the settings name output/series as a write target, and the
     * guard resolves that path through the link. The next case covers what the guard cannot see.
     */
    @Test
    public void aSandboxFolderLinkedOutsideIsCaughtBeforeTheReset() throws Exception {
        run("sandboxInit");
        write(sandbox.resolve("media/episode.mkv"), "media");
        Path outside = libraryShapedFolder(root.resolve("outside"));
        Path series = sandbox.resolve("output/series");
        Files.delete(series);
        Files.createSymbolicLink(series, series.getParent().relativize(outside.resolve("input")));

        BuildResult result = fail("sandboxReset");

        assertThat(result.getOutput(), containsString("outside the sandbox"));
        assertThat(Files.readString(outside.resolve("input/keep.mkv")), is("precious"));
    }

    /**
     * The one case only the clear's own containment check stops. walkFileTree does not follow a link that is
     * the folder itself, but it cannot help when an ancestor is the link: output/series is then a real
     * directory somewhere else, and walking it deletes that directory's contents. The settings are pointed
     * away from output/ first, so the guard has no reason to look there and the check stands alone.
     */
    @Test
    public void resetRefusesAFolderReachedThroughALinkedAncestor() throws Exception {
        run("sandboxInit");
        write(sandbox.resolve("media/episode.mkv"), "media");
        replace(worktree.resolve(".run/sandbox.yaml"), "path: ../sandbox/output/movies/", "path: ../sandbox/elsewhere/movies/");
        replace(worktree.resolve(".run/sandbox.yaml"), "path: ../sandbox/output/series/", "path: ../sandbox/elsewhere/series/");
        Path outside = root.resolve("outside");
        write(outside.resolve("series/keep.mkv"), "precious");
        Files.createDirectories(outside.resolve("movies"));
        Path output = sandbox.resolve("output");
        deleteTree(output);
        Files.createSymbolicLink(output, sandbox.relativize(outside));

        BuildResult result = fail("sandboxReset");

        assertThat(result.getOutput(), containsString("outside the sandbox"));
        assertThat(Files.readString(outside.resolve("series/keep.mkv")), is("precious"));
    }

    @Test
    public void resetUnlinksALinkInsideAClearedFolderInsteadOfFollowingIt() throws Exception {
        run("sandboxInit");
        write(sandbox.resolve("media/episode.mkv"), "media");
        Path outside = libraryShapedFolder(root.resolve("outside"));
        Files.createSymbolicLink(sandbox.resolve("input/sneaky"), outside.resolve("input"));

        run("sandboxReset");

        assertThat("the link itself is gone", Files.exists(sandbox.resolve("input/sneaky"), java.nio.file.LinkOption.NOFOLLOW_LINKS), is(false));
        assertThat("its target is untouched", Files.readString(outside.resolve("input/keep.mkv")), is("precious"));
    }

    // --- the confinement guard: every link of the chain, and that it runs before anything is cleared ---------

    @Test
    public void aSandboxConfigurationPointedAtRealFoldersBlocksTheResetBeforeItClears() throws Exception {
        run("sandboxInit");
        write(sandbox.resolve("input/marker.mkv"), "still here");
        replace(worktree.resolve(".run/Sandbox scan.run.xml"),
                "run --config=.run/sandbox-scan.yaml", "run --config=.run/scan-local.yaml");

        BuildResult result = fail("sandboxReset");

        assertThat(result.getOutput(), containsString("All sandbox runs are blocked"));
        assertThat(result.getOutput(), containsString("Sandbox scan.run.xml"));
        // The reason for testing through Gradle at all: the guard has to run before the clear.
        assertThat(Files.readString(sandbox.resolve("input/marker.mkv")), is("still here"));
    }

    /**
     * Sandbox Kodi runs only the guard, never the reset, so the guard has to pin the root itself. Once a
     * worktree carries its sandbox link, every config path resolves through it, and a sandbox replaced by a
     * link to the library makes all of them look inside: the guard measured "inside" against the link's
     * target, which is the library.
     */
    @Test
    public void theGuardAloneRefusesASandboxReplacedByALink() throws Exception {
        run("sandboxInit");
        Path library = libraryShapedFolder(root.resolve("library"));
        deleteTree(sandbox);
        Files.createSymbolicLink(sandbox, container.relativize(library));

        assertThat(fail("sandboxGuard").getOutput(), containsString("is a symlink"));
        assertThat(Files.readString(library.resolve("input/keep.mkv")), is("precious"));
    }

    /**
     * Reading only the --config value let a configuration run a subcommand directly on any folder: the entry
     * point became the settings file, so sandbox.yaml and its gates never applied.
     */
    @Test
    public void aSandboxConfigurationRunningAnythingButTheRunFormIsRefused() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/Sandbox scan.run.xml"), "run --config=.run/sandbox-scan.yaml",
                "anidb scan --config=.run/sandbox-scan.yaml ../library/input");

        assertThat(fail("sandboxGuard").getOutput(), containsString("not the form 'run --config=<entry point>'"));
    }

    @Test
    public void aMoveFolderOutsideTheSandboxIsRefused() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/sandbox.yaml"), "  move:\n", "  move:\n    folder: ../library/output/\n");

        assertThat(fail("sandboxGuard").getOutput(), containsString("file.move.folder"));
    }

    @Test
    public void aKodiLibraryScanIsRefused() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/sandbox.yaml"), "  libraryScan:\n    enabled: false", "  libraryScan:\n    enabled: true");

        assertThat(fail("sandboxGuard").getOutput(), containsString("kodi.libraryScan.enabled"));
    }

    /**
     * The app removes "x/.." lexically before the filesystem sees the path, while following x first lands
     * somewhere else. The guard has to judge the path the app will actually write to.
     */
    @Test
    public void aDotDotAfterALinkIsJudgedTheWayTheAppResolvesIt() throws Exception {
        run("sandboxInit");
        Files.createSymbolicLink(worktree.resolve(".run/x"), Path.of("../../sandbox/input"));
        replace(worktree.resolve(".run/sandbox.yaml"), "folder: ../sandbox/unknown/", "folder: x/../unknown/");

        assertThat(fail("sandboxGuard").getOutput(), containsString("file.move.unknown.folder"));
    }

    @Test
    public void aSandboxThatIsALinkToNothingYetIsRefusedAndNothingIsCreated() throws Exception {
        Path missing = root.resolve("library/missing");
        Files.createDirectories(missing.getParent());
        Files.createSymbolicLink(sandbox, container.relativize(missing));

        assertThat(fail("sandboxInit").getOutput(), containsString("is a symlink"));
        assertThat("no sandbox tree was built inside the link's target", Files.exists(missing), is(false));
    }

    @Test
    public void aBeforeLaunchStepThatSkipsTheGuardDoesNotCount() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/Sandbox watch.run.xml"), "scriptParameters=\"\"", "scriptParameters=\"-x sandboxGuard\"");

        assertThat(fail("sandboxGuard").getOutput(), containsString("Sandbox watch.run.xml has no plain sandboxReset or sandboxGuard"));
    }

    @Test
    public void aBeforeLaunchStepRunningAnotherProjectDoesNotCount() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/Sandbox watch.run.xml"), "externalProjectPath=\"$PROJECT_DIR$\"", "externalProjectPath=\"/elsewhere\"");

        assertThat(fail("sandboxGuard").getOutput(), containsString("Sandbox watch.run.xml has no plain sandboxReset or sandboxGuard"));
    }

    /** IntelliJ's Copy Configuration puts the copy in workspace.xml, not in .run/. */
    @Test
    public void aCopiedConfigurationInTheWorkspaceIsCheckedToo() throws Exception {
        run("sandboxInit");
        String copy = Files.readString(worktree.resolve(".run/Sandbox scan.run.xml"))
                .replace("name=\"Sandbox scan\"", "name=\"Sandbox scan (1)\"")
                .replace("run --config=.run/sandbox-scan.yaml", "anidb scan -c .run/sandbox.yaml ../library/input")
                .replace("ProjectRunConfigurationManager", "RunManager");
        write(worktree.resolve(".idea/workspace.xml"), "<project version=\"4\">" + copy + "</project>");

        assertThat(fail("sandboxGuard").getOutput(), containsString("workspace.xml (Sandbox scan (1))"));
    }

    @Test
    public void settingsWithoutConfinementAreRefused() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/sandbox.yaml"), "    confineTo: ../sandbox/\n", "");

        assertThat(fail("sandboxGuard").getOutput(), containsString("file.move.confineTo"));
    }

    @Test
    public void aSandboxFolderLinkedAwayIsNotCreatedThroughTheLink() throws Exception {
        Files.createDirectories(sandbox);
        Path share = root.resolve("share");
        Files.createSymbolicLink(sandbox.resolve("output"), sandbox.relativize(share.resolve("output")));

        assertThat(fail("sandboxInit").getOutput(), containsString("is a symlink, so creating"));
        assertThat("nothing was created at the link's target", Files.exists(share), is(false));
    }

    @Test
    public void aRelativeLibraryRootIsRejected() throws Exception {
        Files.writeString(container.resolve(".env"), "ANIDB_USERNAME=someone\nLIBRARY_ROOT=media-lib\n");

        assertThat(fail("envLink").getOutput(), containsString("LIBRARY_ROOT must be an absolute path"));
    }

    @Test
    public void aRemovedBeforeLaunchStepIsItselfAFinding() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/Sandbox watch.run.xml"), "tasks=\"sandboxReset\"", "tasks=\"build\"");

        assertThat(fail("sandboxGuard").getOutput(), containsString("no plain sandboxReset or sandboxGuard before launch"));
    }

    @Test
    public void anEntryPointReadingOutsideTheSandboxIsRefused() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/sandbox-scan.yaml"), "path: ../sandbox/input/", "path: ../library/input/");

        assertThat(fail("sandboxGuard").getOutput(), containsString("reads input from ../library/input/"));
    }

    @Test
    public void settingsThatWriteOutsideTheSandboxAreRefused() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/sandbox.yaml"), "folder: ../sandbox/unknown/", "folder: ../library/unknown/");

        assertThat(fail("sandboxGuard").getOutput(), containsString("file.move.unknown.folder"));
    }

    @Test
    public void anArgThatOverridesAGateIsRefused() throws Exception {
        run("sandboxInit");
        replace(worktree.resolve(".run/sandbox-scan.yaml"),
                "path: ../sandbox/input/", "path: ../sandbox/input/\n    exit-on-ban: false");

        assertThat(fail("sandboxGuard").getOutput(), containsString("sets 'exit-on-ban' in args"));
    }

    // --- envLink and setupCheck --------------------------------------------------------------------------

    @Test
    public void aFreshContainerWithoutACacheIsANoteNotAFailure() throws Exception {
        // An unset ADDITIONAL_ENV is a finding of its own, deliberately, so configure it to isolate the cache.
        Files.writeString(container.resolve("test.env"), "ANIDB_USERNAME=tester\n");
        Files.writeString(container.resolve(".env"), "ANIDB_USERNAME=someone\nADDITIONAL_ENV=test.env\n");
        run("envLink");
        run("sandboxInit");

        BuildResult result = run("setupCheck");

        assertThat(result.getOutput(), containsString("No shared AniDB cache yet"));
        assertThat(result.getOutput(), containsString("Setup looks complete"));
    }

    @Test
    public void envLinkRefusesToReplaceARealFile() throws Exception {
        write(worktree.resolve(".env"), "the only copy of these credentials");

        assertThat(fail("envLink").getOutput(), containsString("refusing to delete it"));
        assertThat(Files.readString(worktree.resolve(".env")), is("the only copy of these credentials"));
    }

    @Test
    public void anAdditionalEnvThatIsAPathIsRejected() throws Exception {
        Files.writeString(container.resolve(".env"), "ADDITIONAL_ENV=../elsewhere.env\n");

        assertThat(fail("envLink").getOutput(), containsString("must be a bare filename"));
    }

    @Test
    public void aDeletedWorktreeIsSkippedRatherThanFailingTheRun() throws Exception {
        Path gone = container.resolve("gone");
        git(container, "worktree", "add", "-q", "--detach", gone.toString(), "main");
        deleteTree(gone);

        run("envLink", "-Pall");

        assertThat(Files.isSymbolicLink(worktree.resolve(".env")), is(true));
    }

    @Test
    public void aNestedWorktreeGetsLinksThatClimbFarEnough() throws Exception {
        Path nested = container.resolve("group/nested");
        git(container, "worktree", "add", "-q", "--detach", nested.toString(), "main");

        runIn(nested, "envLink");

        assertThat(Files.readSymbolicLink(nested.resolve(".env")).toString(), is("../../.env"));
        assertThat(Files.readString(nested.resolve(".env")), containsString("someone"));
    }

    @Test
    public void aPlainCloneRefusesToLinkButStillBuildsItsSandbox() throws Exception {
        Path plain = root.resolve("plain");
        git(root, "clone", "-q", root.resolve("seed").toString(), plain.toString());

        BuildResult refused = runner(plain, "envLink").buildAndFail();
        assertThat(refused.getOutput(), containsString("plain clone"));

        runIn(plain, "sandboxInit");
        assertThat(Files.isDirectory(plain.resolve("sandbox/input")), is(true));
        assertThat("nothing is written above the checkout", Files.exists(root.resolve("sandbox")), is(false));
    }

    // --- fixture -----------------------------------------------------------------------------------------

    /** The smallest project that applies the real script, carrying the real tracked configurations. */
    private static void writeFixtureProject(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("gradle"));
        Files.writeString(dir.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n");
        Files.writeString(dir.resolve("build.gradle.kts"), "apply(from = \"gradle/sandbox.gradle.kts\")\n");
        Files.copy(REPO.resolve("gradle/sandbox.gradle.kts"), dir.resolve("gradle/sandbox.gradle.kts"));
        Files.writeString(dir.resolve(".gitignore"), "/sandbox\n/library\n.env\n*.env\n*.sqlite\n.gradle/\n");
        Path run = Files.createDirectories(dir.resolve(".run"));
        try (Stream<Path> files = Files.list(REPO.resolve(".run"))) {
            for (Path file : files.toList()) {
                Files.copy(file, run.resolve(file.getFileName()));
            }
        }
    }

    /** A folder with the sandbox's own layout, holding one file that must survive. */
    private static Path libraryShapedFolder(Path dir) throws IOException {
        for (String sub : List.of("input", "unknown", "duplicates", "output/movies", "output/series", "media")) {
            Files.createDirectories(dir.resolve(sub));
        }
        Files.writeString(dir.resolve("input/keep.mkv"), "precious");
        return dir;
    }

    private BuildResult run(String... args) {
        return runIn(worktree, args);
    }

    private BuildResult runIn(Path projectDir, String... args) {
        return runner(projectDir, args).build();
    }

    private BuildResult fail(String... args) {
        return runner(worktree, args).buildAndFail();
    }

    private static GradleRunner runner(Path projectDir, String... args) {
        List<String> all = new ArrayList<>(List.of(args));
        all.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                // Kept between runs, so the script's buildscript dependency is downloaded once, not per case.
                .withTestKitDir(REPO.resolve("build/functionalTest-gradle-home").toFile())
                .withArguments(all);
    }

    private static void git(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed in " + dir + ":\n" + output);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void replace(Path file, String from, String to) throws IOException {
        String content = Files.readString(file);
        if (!content.contains(from)) {
            throw new IllegalStateException(file + " does not contain " + from + "; the fixture is out of date");
        }
        Files.writeString(file, content.replace(from, to));
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
