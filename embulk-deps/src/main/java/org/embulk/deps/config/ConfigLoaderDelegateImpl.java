package org.embulk.deps.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.embulk.config.ConfigSource;
import org.embulk.config.DataSource;

public class ConfigLoaderDelegateImpl extends ConfigLoaderDelegate {
    private final ModelManagerDelegateImpl model;

    public ConfigLoaderDelegateImpl(ModelManagerDelegate model) {
        this.model = (ModelManagerDelegateImpl) model;
    }

    @Override
    public ConfigSource newConfigSource() {
        return new DataSourceImpl(model);
    }

    @Override
    public ConfigSource fromJsonString(String string) {
        JsonNode node;
        try {
            node = new ObjectMapper().readTree(string);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        validateJsonNode(node);
        return new DataSourceImpl(model, (ObjectNode) node);
    }

    @Override
    public ConfigSource fromJsonFile(File file) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return fromJson(is);
        }
    }

    @Override
    public ConfigSource fromJson(InputStream stream) throws IOException {
        JsonNode node = new ObjectMapper().readTree(stream);
        validateJsonNode(node);
        return new DataSourceImpl(model, (ObjectNode) node);
    }

    @Override
    public ConfigSource fromYamlString(String string) {
        YamlProcessor yamlProc = YamlProcessor.create(true);
        JsonNode node = objectToJson(yamlProc.load(string));
        validateJsonNode(node);
        return new DataSourceImpl(model, (ObjectNode) node);
    }

    @Override
    public ConfigSource fromYamlFile(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return fromYaml(stream);
        }
    }

    @Override
    public ConfigSource fromYaml(InputStream stream) throws IOException {
        YamlProcessor yamlProc = YamlProcessor.create(true);
        JsonNode node = objectToJson(yamlProc.load(stream));
        validateJsonNode(node);
        return new DataSourceImpl(model, (ObjectNode) node);
    }

    private static void validateJsonNode(JsonNode node) {
        if (!node.isObject()) {
            throw new RuntimeJsonMappingException("Expected object to load ConfigSource but got " + node);
        }
    }

    // To be removed by v0.10. Not used from Embulk core.
    @Deprecated  // https://github.com/embulk/embulk/issues/934
    @SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
    public ConfigSource fromJson(JsonParser parser) throws IOException {
        // TODO check parsed.isObject()
        ObjectNode source = (ObjectNode) new ObjectMapper().readTree(parser);
        return new DataSourceImpl(model, source);
    }

    @Override
    public ConfigSource fromPropertiesYamlLiteral(Properties props, String keyPrefix) {
        final LinkedHashMap<String, String> builder = new LinkedHashMap<>();
        for (String propName : props.stringPropertyNames()) {
            builder.put(propName, props.getProperty(propName));
        }
        return fromPropertiesYamlLiteral(Collections.unmodifiableMap(builder), keyPrefix);
    }

    @Override
    public ConfigSource fromPropertiesYamlLiteral(Map<String, String> props, String keyPrefix) {
        ObjectNode source = new ObjectNode(JsonNodeFactory.instance);
        DataSource ds = new DataSourceImpl(model, source);
        YamlProcessor yamlProc = YamlProcessor.create(true);
        for (Map.Entry<String, String> pair : props.entrySet()) {
            if (!pair.getKey().startsWith(keyPrefix)) {
                continue;
            }
            String keyName = pair.getKey().substring(keyPrefix.length());
            Object parsedValue = yamlProc.load(pair.getValue());
            JsonNode node = objectToJson(parsedValue);

            // handle "." as a map acccessor. for example:
            // in.parser.type=csv => {"in": {"parser": {"type": "csv"}}}
            // TODO handle "[]" as array index
            String[] fragments = keyName.split("\\.");
            DataSource key = ds;
            for (int i = 0; i < fragments.length - 1; i++) {
                key = key.getNestedOrSetEmpty(fragments[i]);  // TODO exception handling
            }
            key.set(fragments[fragments.length - 1], node);
        }
        return new DataSourceImpl(model, source);
    }

    /**
     * Creates ConfigSource from java.util.Properties as-is.
     *
     * <p>Users and plugins MUST NOT call this directly. No any compatibility is guaranteed.
     */
    @Deprecated
    @Override
    public ConfigSource fromPropertiesAsIs(final Properties properties) {
        final ObjectNode sourceNode = new ObjectNode(JsonNodeFactory.instance);
        final DataSource dataSource = new DataSourceImpl(model, sourceNode);
        for (final String key : properties.stringPropertyNames()) {
            dataSource.set(key, properties.getProperty(key));
        }
        return new DataSourceImpl(model, sourceNode);
    }

    private JsonNode objectToJson(Object object) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(objectMapper.writeValueAsString(object));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
