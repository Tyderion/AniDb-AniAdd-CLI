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

        mYaml = new Yaml(new PathConstructor<>(clazz, loaderoptions), representer, options);
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
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
            // if value of property is null, ignore it.
            if (propertyValue == null) {
                return null;
            } else if (Path.class.isAssignableFrom(propertyValue.getClass())) {
                return super.representJavaBeanProperty(javaBean, property, ((Path) propertyValue).toString(), customTag);
            } else {
                return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            }
        }
    }

    private static class PathConstructor<T> extends Constructor {
        public PathConstructor(Class<T> clazz, LoaderOptions loaderoptions) {
            super(clazz, loaderoptions);
            this.yamlClassConstructors.put(NodeId.scalar, new ConstructScalar() {
                @Override
                public Object construct(Node node) {
                    if (Path.class == node.getType()) {
                        return Path.of(((ScalarNode) node).getValue());
                    }
                    return super.construct(node);
                }
            });
        }
    }
}
