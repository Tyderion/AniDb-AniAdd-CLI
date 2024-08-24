import config.blocks.TagsConfig;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import processing.FileHandler;
import processing.FileRenamer;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

public class FileHandlerTest {
    private static final String BASE_PATH = "build/test/";

    private TestPath testVideoFile = TestPath.of("fake-video.mkv");

    @BeforeAll
    public static void setUp() {
        try {
            Files.createDirectories(Path.of(BASE_PATH));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void singleSetup() {
        createFile(testVideoFile);
    }

    @AfterEach
    public void tearDown() {
        try(val walk = Files.walk(Path.of(BASE_PATH))) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void deleteFile_should_deleteFile() {
        // Arrange
        createFile(testVideoFile);
        assertThat(exists(testVideoFile), is(true));
        val fileHandler = new FileHandler();
        // Act

        fileHandler.deleteFile(testVideoFile.getAbsolutePath());
        // Assert
        assertThat(exists(testVideoFile), is(false));
    }


    @Test
    public void renameFile_Should_CorrectlyRenameFile() {
        // Arrange
        createFile(testVideoFile);
        assertThat(exists(testVideoFile), is(true));
        val fileHandler = new FileHandler();
        val targetFile = TestPath.of("renamed-video.mkv");
        // Act

        fileHandler.renameFile(testVideoFile.getAbsolutePath(), targetFile.getAbsolutePath());
        // Assert
        assertThat(exists(testVideoFile), is(false));
        assertThat(exists(targetFile), is(true));
    }

    @Test
    public void renameFile_Should_LeaveAsIsIfSame() {
        // Arrange
        createFile(testVideoFile);
        assertThat(exists(testVideoFile), is(true));
        val fileHandler = new FileHandler();
        // Act

        fileHandler.renameFile(testVideoFile.getAbsolutePath(), testVideoFile.getAbsolutePath());
        // Assert
        assertThat(exists(testVideoFile), is(true));
    }

    @Test
    public void renameFile_Should_CorrectlyHandleDirectories() {
        // Arrange
        createFile(testVideoFile);
        assertThat(exists(testVideoFile), is(true));
        val fileHandler = new FileHandler();
        val targetFile = TestPath.of("nested", "directories", "renamed-video.mkv");
        // Act

        fileHandler.renameFile(testVideoFile.getAbsolutePath(), targetFile.getAbsolutePath());
        // Assert
        assertThat(exists(testVideoFile), is(false));
        assertThat(exists(targetFile), is(true));
    }


    private boolean exists(TestPath path) {
        return Files.exists(path.getAbsolutePath());
    }


    private void createFile(TestPath path) {
        try {
            Files.createFile(path.getAbsolutePath());
        } catch (FileAlreadyExistsException _) {
            // Ignore
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RequiredArgsConstructor
    private static class TestPath {
        private final Path path;

        public static TestPath of(String... path) {
            if (path.length == 1) {
                return new TestPath(Path.of(path[0]));
            }
            return new TestPath(Path.of(path[0], Arrays.copyOfRange(path, 1, path.length)));
        }

        public Path getAbsolutePath() {
            return Path.of(BASE_PATH).resolve(path);
        }
    }
}
