package aniAdd.config;

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
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class ConfigFileParser<T> {

    private final Class<T> mClazz;
    private final Yaml mYaml;
    private final String mConfigFilePath;

    public ConfigFileParser(String configFilePath, Class<T> clazz) {
        mConfigFilePath = configFilePath;
        mClazz = clazz;
        log.warn("If you upgrade from an old configuration file make sure to check the new one and adjust paths if necessary. Check the documentation of AniConfiguration.");

        var loaderoptions = new LoaderOptions();
        TagInspector taginspector =
                tag -> {
                    if (tag.getClassName().equals("aniAdd.config.XBMCDefaultConfiguration")) {
                        log.warn("Converted from old configuration file.");
                        return true;
                    }
                    return tag.getClassName().equals(AniConfiguration.class.getName()) || tag.getClassName().equals("aniAdd.config.AniConfiguration");
                };

        Representer representer = new Representer(new DumperOptions());
        representer.getPropertyUtils().setSkipMissingProperties(true);
        loaderoptions.setTagInspector(taginspector);
        val options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        mYaml = new Yaml(new Constructor(AniConfiguration.class, loaderoptions), representer, options);
        mYaml.setBeanAccess(BeanAccess.FIELD);
    }

    public Optional<T> loadFromFile(boolean useDefault) {
        InputStream input = null;
        try {
            input = new FileInputStream(mConfigFilePath);
        } catch (FileNotFoundException e) {
            log.error(STR."File not found at: \{mConfigFilePath}");
            if (!useDefault) {
                return Optional.empty();
            }
        }
        if (input != null) {
            return Optional.of(mYaml.loadAs(input, mClazz));
        } else {
            try {
                log.warn("Using default configuration");
                return Optional.of(mClazz.getDeclaredConstructor().newInstance());
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        }
    }

    public void saveToFile(T configuration) throws IOException {
        saveToFile(configuration, mConfigFilePath);
    }

    public void saveToFile(T configuration, String path) throws IOException {
        File file = new File(path);
        Writer writer = new BufferedWriter(new FileWriter(file));
        log.info(STR."Saving config to file: \{file.getAbsolutePath()}");
        mYaml.dump(configuration, writer);
    }
}
