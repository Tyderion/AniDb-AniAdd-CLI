import config.CliConfiguration;
import config.RunConfig;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import utils.config.ConfigFileParser;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RunConfigTest {

    @Test
    public void Should_CorrectlyParse_SimpleScanYaml() {
        assertConfig("scan_simple",
                List.of("anidb", "scan", "/path/to/scan", "--config=subconfig.yaml")
        );
    }

    @Test
    public void Should_NotAcceptPassword_FromConfig() {
        assertConfigError(
                "password",
                "Password must not be provided in the config file. Use the command line or env instead."
        );
    }

    @Test
    public void Should_CorrectlyParse_SimpleWatchYaml() {
        assertConfig(
                "watch_simple",
                List.of("anidb", "watch", "/path/to/watch", "--config=config.yaml")
        );
    }

    @Test
    public void Should_CorrectlyInferWatchAndKodi_IfWatchAndKodiIsActive() {
        assertConfig(
                "watch_and_kodi",
                List.of("anidb", "watch", "/path/to/files", "--kodi=true", "--config=config.yaml")
        );
    }

    @Test
    public void Should_CorrectlyParse_OverriddenArguments() {
        assertConfig(
                "watch_with_arguments",
                List.of("anidb", "watch",  "/path/to/watch", "--interval=17", "--exit-on-ban=true", "--localport=4444",  "--config=config.yaml")
        );
    }

    private void assertConfig(String filename, List<String> expected) {
        val runConfig = getConfig(getYamlFile(filename));
        val result = assertDoesNotThrow(() -> runConfig.toCommandArgs(Path.of(STR."\{filename}.yaml")));
        assertThat(result, is(expected));
    }

    private void assertConfigError(String filename, String expected) {
        val runConfig = getConfig(getYamlFile(filename));
        val result = assertThrows(RunConfig.InvalidConfigException.class, () -> runConfig.toCommandArgs(Path.of(STR."\{filename}.yaml")));
        assertThat(result.getMessage(), is(expected));
    }

    @NotNull
    private RunConfig getConfig(InputStream input) {
        val parser = new ConfigFileParser<>(CliConfiguration.class);
        val parsed = parser.load(input);
        assertNotNull(parsed);
        val runConfig = parsed.run();
        assertNotNull(runConfig);
        return runConfig;
    }

    private InputStream getYamlFile(String filename) {
        return getClass().getClassLoader().getResourceAsStream(STR."run_templates/\{filename}.yaml");
    }
}
