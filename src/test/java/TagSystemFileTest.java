import config.RootConfiguration;
import config.blocks.TagsConfig;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.config.ConfigFileHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A tag system is around fifty lines and every config that renames files needs the same one. Inlining it
 * meant a copy per config, and the copies drift. tagSystemFile points at one shared definition instead.
 */
public class TagSystemFileTest {

    private static final String TAG_SYSTEM = """
            ShowTitle:=[%ATr%, %ATe%]
            FileName:=%ShowTitle%
            """;

    @Test
    public void theTagSystemIsReadFromTheReferencedFile(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("tags.txt"), TAG_SYSTEM);
        val config = write(dir.resolve("settings.yaml"), """
                tags:
                  tagSystemFile: tags.txt
                """);
        assertThat(config.tags().tagSystem(), is(TAG_SYSTEM));
    }

    @Test
    public void theReferenceFollowsTheSamePathRuleAsEverythingElse(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("tags.txt"), TAG_SYSTEM);
        val nested = Files.createDirectories(dir.resolve("run"));
        val config = write(nested.resolve("settings.yaml"), """
                tags:
                  tagSystemFile: ../tags.txt
                """);
        assertThat(config.tags().tagSystem(), is(TAG_SYSTEM));
    }

    @Test
    public void anInlineTagSystemStillWins(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("tags.txt"), TAG_SYSTEM);
        val config = write(dir.resolve("settings.yaml"), """
                tags:
                  tagSystemFile: tags.txt
                  tagSystem: |-
                    FileName:="inline"
                """);
        assertThat(config.tags().tagSystem(), is("FileName:=\"inline\""));
    }

    @Test
    public void aMissingFileReportsRatherThanCrashing(@TempDir Path dir) throws IOException {
        val config = write(dir.resolve("settings.yaml"), """
                tags:
                  tagSystemFile: nope.txt
                """);
        assertThat(config.tags().tagSystem(), is(nullValue()));
    }

    /**
     * The cache field must stay out of the serialized form, or saving a config would write the whole tag
     * system back inline and undo the sharing.
     */
    @Test
    public void theCachedContentIsNeverWrittenBackOut(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("tags.txt"), TAG_SYSTEM);
        val config = write(dir.resolve("settings.yaml"), """
                tags:
                  tagSystemFile: tags.txt
                """);
        assertNotNull(config.tags().tagSystem(), "tag system should have been loaded and cached by now");

        val writer = new StringWriter();
        new utils.config.ConfigFileParser<>(TagsConfig.class).dump(config.tags(), writer);
        assertThat(writer.toString(), containsString("tagSystemFile"));
        assertThat(writer.toString(), not(containsString("loadedTagSystem")));
        assertThat(writer.toString(), not(containsString("ShowTitle")));
    }

    private RootConfiguration write(Path file, String yaml) throws IOException {
        Files.writeString(file, yaml);
        val config = new ConfigFileHandler<>(RootConfiguration.class).getConfiguration(file);
        assertNotNull(config, STR."failed to load \{file}");
        return config;
    }
}
