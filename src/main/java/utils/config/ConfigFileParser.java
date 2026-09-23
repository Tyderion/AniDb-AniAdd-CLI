package utils.config;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.representer.Representer;

import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;

@Slf4j
public class ConfigFileParser<T> {
    private final Class<T> clazz;
    private final Yaml mYaml;

    public ConfigFileParser(Class<T> clazz) {
        this(clazz, null);
    }

    /**
     * @param baseDir directory that relative paths in the file are resolved against, the way compose files
     *                and tsconfig behave. Pass the config file's own directory so a config means the same
     *                thing wherever it is launched from. Null keeps paths exactly as written.
     */
    public ConfigFileParser(Class<T> clazz, Path baseDir) {
        this.clazz = clazz;
        var loaderoptions = new LoaderOptions();
        loaderoptions.setEnumCaseSensitive(false);
        TagInspector taginspector =
                tag -> tag.getClassName().equals(clazz.getName());
        loaderoptions.setTagInspector(taginspector);

        val options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Representer representer = new PathRepresenter(options);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        mYaml = new Yaml(new PathConstructor<>(clazz, loaderoptions, baseDir), representer, options);
        mYaml.setBeanAccess(BeanAccess.FIELD);
    }

    public T load(InputStream input) {
        return mYaml.loadAs(input, clazz);
    }

    public void dump(T configuration, Writer output) {
        mYaml.dump(configuration, output);
    }

    private static class PathRepresenter extends Representer {
        public PathRepresenter(DumperOptions options) {
            super(options);
            this.multiRepresenters.put(Path.class, node -> {
                if (node == null) {
                    return representScalar(Tag.NULL, "");
                }
                var value = ((Path) node).toString();
                if (!value.matches("^[A-Z]:.*")) {
                    value = value.replace("\\", "/");
                }
                return representScalar(Tag.STR, value, null);
            });
            this.multiRepresenters.put(Enum.class, node -> {
                Tag tag = new Tag(node.getClass());
                return representScalar(getTag(node.getClass(), tag), ((Enum<?>) node).name().toLowerCase());
            });
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
            // if value of property is null, ignore it.
            if (propertyValue == null) {
                return null;
            }
            return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
        }
    }

    private static class PathConstructor<T> extends Constructor {
        public PathConstructor(Class<T> clazz, LoaderOptions loaderoptions, Path baseDir) {
            super(clazz, loaderoptions);
            this.yamlClassConstructors.put(NodeId.scalar, new ConstructScalar() {
                @Override
                public Object construct(Node node) {
                    if (Path.class == node.getType()) {
                        return resolve(Path.of(((ScalarNode) node).getValue()), baseDir);
                    }
                    return super.construct(node);
                }
            });
        }
    }

    /**
     * Every path-typed value in a config file goes through here, so this is the single place that decides
     * what a relative path means. Absolute paths and configs loaded without a base are left untouched.
     */
    public static Path resolve(Path path, Path baseDir) {
        // A blank value means unset to the code reading it (a blank move folder renames in place, a blank
        // cache path is refused as missing). Resolving it would turn it into the config's own directory.
        if (baseDir == null || path.isAbsolute() || path.toString().isBlank()) {
            return path;
        }
        return baseDir.toAbsolutePath().resolve(path).normalize();
    }

    /**
     * The directory a config file's relative paths resolve against: the real one, with symlinks followed.
     * Shared setups link one config into several checkouts, and a link should behave like the file it points
     * at rather than like wherever the link happens to sit. Falls back to the literal location if the path
     * cannot be resolved, which mainly means it does not exist yet.
     */
    public static Path baseDirectoryOf(Path configFile) {
        try {
            return configFile.toRealPath().getParent();
        } catch (java.io.IOException e) {
            return configFile.toAbsolutePath().normalize().getParent();
        }
    }
}
