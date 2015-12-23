package aniAdd.config;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Archie on 23.12.2015.
 */
public class ConfigFileParser<T> {

    private Yaml mYaml;
    private String mConfigFilePath;

    public ConfigFileParser(String configFilePath) {
        mConfigFilePath = configFilePath;
        mYaml = new Yaml();
    }

    public T loadFromFile(String path) {
        InputStream input = null;
        try {
            input = new FileInputStream(new File(path));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return (T) mYaml.load(input);
    }

    public void saveToFile(T configuration) throws IOException {
        File file = new File(mConfigFilePath);
        Writer writer = new BufferedWriter(new FileWriter(file));
        Logger.getGlobal().log(Level.WARNING, "FUll file path: " + file.getAbsolutePath());
        mYaml.dump(configuration, writer);
    }
}
