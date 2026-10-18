package org.jaldrishti.sim.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Loads scenario YAML definitions into typed {@link Scenario} instances.
 */
public class ScenarioLoader {

    private final ObjectMapper yamlMapper;

    public ScenarioLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public Scenario load(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        return yamlMapper.readValue(file, Scenario.class);
    }

    public Scenario load(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        return yamlMapper.readValue(inputStream, Scenario.class);
    }

    public Scenario loadFromString(String yamlContent) throws IOException {
        Objects.requireNonNull(yamlContent, "yamlContent must not be null");
        return yamlMapper.readValue(yamlContent, Scenario.class);
    }
}
