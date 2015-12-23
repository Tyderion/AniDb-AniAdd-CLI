package aniAdd.config;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Archie on 23.12.2015.
 */
public class ConfigFileParser<T, U extends T> {

    private final Class<U> mClazz;
    private Yaml mYaml;
    private String mConfigFilePath;

    public ConfigFileParser(String configFilePath, Class<U> clazz) {
        mConfigFilePath = configFilePath;
        mYaml = new Yaml();
        mClazz = clazz;
    }

    public T loadFromFile() {
        InputStream input = null;
        try {
            input = new FileInputStream(new File(mConfigFilePath));
        } catch (FileNotFoundException e) {
            Logger.getGlobal().log(Level.WARNING, "File not found");
        }
        if (input != null) {
            return (T) mYaml.load(input);
        } else {
            try {
                return mClazz.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
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
        Logger.getGlobal().log(Level.WARNING, "FUll file path: " + file.getAbsolutePath());
        mYaml.dump(configuration, writer);
    }
}
