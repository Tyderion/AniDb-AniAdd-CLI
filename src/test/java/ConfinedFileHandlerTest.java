import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.ConfinedFileHandler;
import processing.IFileHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Every move, rename and delete in processing goes through an IFileHandler, so confining that is what keeps a
 * sandbox run inside the sandbox even when the destination is computed at run time, for example by a tag
 * system writing an absolute path. Judged by real location, so a symlink inside the sandbox cannot be used to
 * reach out of it.
 */
public class ConfinedFileHandlerTest {

    @TempDir
    Path tempDir;

    Path sandbox;
    Path outside;
    final List<String> performed = new ArrayList<>();
    ConfinedFileHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        Path root = tempDir.toRealPath();
        sandbox = Files.createDirectories(root.resolve("sandbox"));
        outside = Files.createDirectories(root.resolve("library"));
        handler = new ConfinedFileHandler(new IFileHandler() {
            @Override
            public boolean renameFile(Path from, Path to) {
                performed.add("rename " + from + " -> " + to);
                return true;
            }

            @Override
            public void deleteFile(Path path) {
                performed.add("delete " + path);
            }
        }, sandbox);
    }

    @Test
    public void aMoveInsideTheSandboxGoesThrough() {
        assertThat(handler.renameFile(sandbox.resolve("input/a.mkv"), sandbox.resolve("output/series/Show/a.mkv")), is(true));
        assertThat(performed, hasSize(1));
    }

    @Test
    public void aMoveToAnAbsolutePathOutsideIsRefused() {
        // What a tag system writing PathName:="/mnt/library/..." would produce.
        assertThat(handler.renameFile(sandbox.resolve("input/a.mkv"), outside.resolve("series/a.mkv")), is(false));
        assertThat(performed, is(empty()));
    }

    @Test
    public void aMoveOutOfARealFolderIsRefused() {
        // A sandbox config pointed at a real input folder would otherwise take files out of the library.
        assertThat(handler.renameFile(outside.resolve("a.mkv"), sandbox.resolve("output/a.mkv")), is(false));
        assertThat(performed, is(empty()));
    }

    @Test
    public void aLinkInsideTheSandboxCannotBeUsedToReachOut() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("escape"), outside);

        assertThat(handler.renameFile(sandbox.resolve("input/a.mkv"), sandbox.resolve("escape/series/a.mkv")), is(false));
        assertThat(performed, is(empty()));
    }

    @Test
    public void aDeleteOutsideIsRefused() {
        handler.deleteFile(outside.resolve("a.mkv"));
        assertThat(performed, is(empty()));

        handler.deleteFile(sandbox.resolve("input/a.mkv"));
        assertThat(performed, hasSize(1));
    }
}
