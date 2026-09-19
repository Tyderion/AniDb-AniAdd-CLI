import config.blocks.FileConfig;
import config.blocks.MoveConfig;
import config.blocks.RenameConfig;
import config.blocks.TagsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.FileHandler;
import processing.FileInfo;
import processing.FileRenamer;
import processing.tagsystem.TagSystemTags;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FileRenamerTest {
    private static final String TAG_SYSTEM = "FileName:=%ATr% \" - \" %EpNo%";

    private FileInfo fileInfo(Path file, FileConfig config) {
        FileInfo info = new FileInfo(file.toFile(), 0, null, config);
        info.getData().put(TagSystemTags.SeriesNameRomaji, "Show");
        info.getData().put(TagSystemTags.EpisodeNumber, "1");
        return info;
    }

    @Test
    public void aCorrectlyNamedFileIsNotTreatedAsItsOwnDuplicate(@TempDir Path tempDir) throws Exception {
        // Rescanning a library used to find the file at its own target and delete it as a duplicate.
        Path file = Files.writeString(tempDir.resolve("Show - 1.mkv"), "video");
        FileConfig config = FileConfig.builder()
                .rename(RenameConfig.builder().mode(RenameConfig.Mode.TAGSYSTEM).build())
                .move(MoveConfig.builder()
                        .mode(MoveConfig.Mode.FOLDER)
                        .folder(tempDir)
                        .duplicates(MoveConfig.HandlingConfig.builder().mode(MoveConfig.HandlingConfig.Mode.DELETE).build())
                        .build())
                .build();

        boolean renamed = new FileRenamer(new FileHandler(), TagsConfig.builder().tagSystem(TAG_SYSTEM).build()).renameFile(fileInfo(file, config));

        assertThat(renamed, is(true));
        assertThat(Files.exists(file), is(true));
    }

    @Test
    public void relatedFilesFollowOnlyWhenTheirNameContinuesWithASeparator(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("Episode 1.mkv"), "video");
        Files.writeString(tempDir.resolve("Episode 1.nfo"), "nfo");
        Files.writeString(tempDir.resolve("Episode 1-thumb.jpg"), "thumb");
        Files.writeString(tempDir.resolve("Episode 1.en.ass"), "subs");
        Files.writeString(tempDir.resolve("Episode 10.nfo"), "another episode");
        FileConfig config = FileConfig.builder()
                .rename(RenameConfig.builder().mode(RenameConfig.Mode.TAGSYSTEM).related(true).build())
                .build();
        FileInfo info = fileInfo(file, config);

        boolean renamed = new FileRenamer(new FileHandler(), TagsConfig.builder().tagSystem(TAG_SYSTEM).build()).renameFile(info);

        assertThat(renamed, is(true));
        assertThat(info.getWorkingFile().toPath(), is(tempDir.resolve("Show - 1.mkv")));
        assertThat(Files.exists(tempDir.resolve("Show - 1.nfo")), is(true));
        assertThat(Files.exists(tempDir.resolve("Show - 1-thumb.jpg")), is(true));
        assertThat(Files.exists(tempDir.resolve("Show - 1.en.ass")), is(true));
        assertThat(Files.exists(tempDir.resolve("Episode 10.nfo")), is(true));
    }

    @Test
    public void renameModeNoneKeepsTheNameWithoutDoublingTheExtension(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("as is.mkv"), "video");
        FileConfig config = FileConfig.builder()
                .move(MoveConfig.builder().mode(MoveConfig.Mode.FOLDER).folder(tempDir.resolve("moved")).build())
                .build();
        FileInfo info = fileInfo(file, config);

        new FileRenamer(new FileHandler(), TagsConfig.builder().tagSystem(TAG_SYSTEM).build()).renameFile(info);

        assertThat(Files.exists(tempDir.resolve("moved/as is.mkv")), is(true));
    }
}
