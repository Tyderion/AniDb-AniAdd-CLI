package utils.config;

import config.CliConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public class ConfigFileParser<T> {

    private final Class<T> clazz;
    private final Yaml mYaml;

    public ConfigFileParser(Class<T> clazz) {
        this.clazz = clazz;
        var loaderoptions = new LoaderOptions();
        loaderoptions.setEnumCaseSensitive(false);
        TagInspector taginspector =
                tag -> tag.getClassName().equals(CliConfiguration.class.getName());
        loaderoptions.setTagInspector(taginspector);

        val options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Representer representer = new Representer(options);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        mYaml = new Yaml(new Constructor(clazz, loaderoptions), representer, options);
        mYaml.setBeanAccess(BeanAccess.FIELD);
    }

    public T loadFromFile(Path configFilePath) {
        if (configFilePath == null || configFilePath.toString().isBlank()) {
            return null;
        }
        try {
            val input = new FileInputStream(configFilePath.toFile());
            return mYaml.loadAs(input, clazz);
        } catch (FileNotFoundException e) {
            log.error(STR."File not found at: \{configFilePath}");
            return null;
        }
    }

    public void saveToFile(String path, T configuration, boolean overwrite) throws IOException {
        saveToFile(Path.of(path), configuration, overwrite);
    }

    public void saveToFile(Path path, T configuration, boolean overwrite) throws IOException {
        if (overwrite || !Files.exists(path)) {
            Writer writer = new BufferedWriter(new FileWriter(path.toFile()));
            log.info(STR."Saving config to file: \{path.toAbsolutePath()}");
            mYaml.dump(configuration, writer);
        }
    }
}
