package aniAdd.config;

import lombok.extern.java.Log;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Log
public class ConfigFileParser<T> {

    private final Class<T> mClazz;
    private final Yaml mYaml;
    private final String mConfigFilePath;

    public ConfigFileParser(String configFilePath, Class<T> clazz) {
        mConfigFilePath = configFilePath;
        mClazz = clazz;
        log.warning("If you upgrade from an old configuration file make sure to check the new one and adjust paths if necessary. Check the documentation of AniConfiguration.");

        var loaderoptions = new LoaderOptions();
        TagInspector taginspector =
                tag -> {
                    if (tag.getClassName().equals("aniAdd.config.XBMCDefaultConfiguration")) {
                        log.warning("Converted from old configuration file.");
                        return true;
                    }
                    return tag.getClassName().equals(AniConfiguration.class.getName());
                };
        Representer representer = new Representer(new DumperOptions());
        representer.getPropertyUtils().setSkipMissingProperties(true);
        loaderoptions.setTagInspector(taginspector);

        mYaml = new Yaml(new Constructor(AniConfiguration.class, loaderoptions), representer);
        mYaml.setBeanAccess(BeanAccess.FIELD);
    }

    public T loadFromFile() {
        InputStream input = null;
        try {
            input = new FileInputStream(new File(mConfigFilePath));
        } catch (FileNotFoundException e) {
            Logger.getGlobal().log(Level.WARNING, "File not found");
        }
        if (input != null) {
            return mYaml.loadAs(input, mClazz);
        } else {
            try {
                Logger.getGlobal().log(Level.WARNING, "Using default configuration");
                return mClazz.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public void saveToFile(T configuration) throws IOException {
        saveToFile(configuration, mConfigFilePath);
    }

    public void saveToFile(T configuration, String path) throws IOException {
        File file = new File(path);
        Writer writer = new BufferedWriter(new FileWriter(file));
        log.info("Saving config to file: " + file.getAbsolutePath());
        mYaml.dump(configuration, writer);
    }
}
