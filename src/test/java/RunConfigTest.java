import config.CliConfiguration;
import config.RunConfig;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import utils.config.ConfigFileParser;
import utils.config.SecretsLoader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RunConfigTest {


    @Test
    public void Should_CorrectlyParse_SimpleScanYaml() {
        assertConfig("scan_simple",
                Map.of(
                        "ANIDB_PASSWORD", "password1"
                ),
                List.of("anidb", "--password=password1", "--config=subconfig.yaml", "scan", "/path/to/scan")
        );
    }


    @Test
    public void Should_CorrectlyUseUserInfo_FromSecretsStore() {
        assertConfig(
                "scan_simple",
                List.of("anidb", "--password=password1", "--username=username1", "--config=subconfig.yaml", "scan", "/path/to/scan")
        );
    }

    @Test
    public void Should_CorrectlyParse_SimpleWatchYaml() {
        assertConfig(
                "watch_simple",
                List.of("anidb", "--password=password1", "--username=username1", "--config=config.yaml", "watch", "/path/to/watch")
        );
    }

    @Test
    public void Should_CorrectlyInferWatch_IfWatchAndScanIsActive() {
        assertConfig(
                "watch_and_scan",
                List.of("anidb", "--password=password1", "--username=username1", "--config=config.yaml", "watch", "/path/to/files")
        );
    }

    @Test
    public void Should_CorrectlyInferWatchAndKodi_IfWatchAndKodiIsActive() {
        assertConfig(
                "watch_and_kodi",
                List.of("anidb", "--password=password1", "--username=username1", "--config=config.yaml", "watch-and-kodi", "/path/to/files")
        );
    }

    @Test
    public void Should_CorrectlyInferWatchAndKodi_IfScanAndKodiIsActive() {
        assertConfig(
                "scan_and_kodi",
                List.of("anidb", "--password=password1", "--username=username1", "--config=config.yaml", "watch-and-kodi", "/path/to/files")
        );
    }

    @Test
    public void Should_CorrectlyParse_OverriddenArguments() {
        assertConfig(
                "watch_with_arguments",
                List.of("anidb", "--password=password1", "--username=username1","--localport=4444", "--exit-on-ban=true", "--config=config.yaml",  "watch", "--interval=17", "/path/to/watch")
        );
    }

    @Test
    public void ShouldThrow_IfNoPasswordSet() {
        assertConfigError(
                "watch_with_arguments",
                Map.of(),
                "ANIDB_PASSWORD environment variable not set."
        );
    }

    private void assertConfig(String filename, List<String> expected) {
        assertConfig(filename, Map.of(
                "ANIDB_USERNAME", "username1",
                "ANIDB_PASSWORD", "password1"
        ), expected);
    }

    private void assertConfig(String filename, Map<String, String> secrets, List<String> expected) {
        val runConfig = getConfig(getYamlFile(filename));
        val result = assertDoesNotThrow(() -> runConfig.toCommandArgs(Path.of(STR."\{filename}.yaml"), getSecretsMock(secrets)));
        assertThat(result, is(expected));
    }

    private void assertConfigError(String filename, Map<String, String> secrets, String expected) {
        val runConfig = getConfig(getYamlFile(filename));
        val result = assertThrows(RunConfig.InvalidConfigException.class, () -> runConfig.toCommandArgs(Path.of(STR."\{filename}.yaml"), getSecretsMock(secrets)));
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

    private SecretsLoader getSecretsMock(Map<String, String> answers) {
        val secretsMock = Mockito.mock(SecretsLoader.class);
        doAnswer(ele -> answers.get(ele.getArgument(0, String.class))).when(secretsMock).getSecret(anyString());
        return secretsMock;
    }

    private InputStream getYamlFile(String filename) {
        return getClass().getClassLoader().getResourceAsStream(STR."run_templates/\{filename}.yaml");
    }
}
