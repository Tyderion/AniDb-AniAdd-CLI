package aniAdd.config;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Archie on 23.12.2015.
 */
public class ConfigFileParser<T> {

    private final Class<T> mClazz;
    private final Yaml mYaml = new Yaml();
    private final String mConfigFilePath;

    public ConfigFileParser(String configFilePath, Class<T> clazz) {
        mConfigFilePath = configFilePath;
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
            return mYaml.loadAs(input, mClazz);
        } else {
            try {
                return mClazz.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
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
