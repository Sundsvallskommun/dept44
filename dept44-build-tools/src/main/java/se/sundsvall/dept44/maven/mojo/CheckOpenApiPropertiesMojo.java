package se.sundsvall.dept44.maven.mojo;

import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Mojo(name = "check-openapi-properties", defaultPhase = LifecyclePhase.INITIALIZE)
public class CheckOpenApiPropertiesMojo extends AbstractDept44CheckMojo {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final String PROPERTY_FILE_PATTERN = "^application(.yaml|.yml|.properties)$";
    private static final String YAML_FILE_EXTENSION_PATTERN = ".*(.yaml|.yml)$";

    @Parameter(property = "dept44.check.openapi-properties.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void doExecute() throws MojoFailureException {
        // In addition to checking regular skip flags - a quick and dirty "workaround" to pass on
        // dept44 framework stuff that doesn't have OpenAPI properties...
        if (isSkipAllChecks() || skip || "pom".equalsIgnoreCase(getProject().getPackaging())) {
            getLog().info("Skipping validation of OpenAPI properties");

            return;
        }

        getLog().info("Validating OpenAPI properties");

        validateOpenApiProperties();
    }

    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    private void validateOpenApiProperties() throws MojoFailureException {
        var openApiEnabled = true;
        var openApiNamePropertySet = false;
        var openApiTitlePropertySet = false;
        var openApiVersionPropertySet = false;

        var propertyFiles = new File(getProject().getBasedir(), "src/main/resources")
            .listFiles((dir, name) -> name.matches(PROPERTY_FILE_PATTERN));

        // We should have some files to work with...
        if (propertyFiles == null || propertyFiles.length == 0) {
            throw new MojoFailureException("No application properties/YAML files exist");
        }

        for (var propertyFile : propertyFiles) {
            var properties = loadProperties(propertyFile);

            openApiEnabled = ofNullable(properties.getOrDefault("openapi.enabled", "true"))
                .map(Object::toString)
                .map(Boolean::valueOf)
                .orElse(true);

            // If OpenAPI isn't enabled there's no need to check for the other properties
            if (!openApiEnabled) {
                break;
            }

            openApiNamePropertySet |= isPropertySetAndNonEmpty(properties, "openapi.name");
            openApiTitlePropertySet |= isPropertySetAndNonEmpty(properties, "openapi.title");
            openApiVersionPropertySet |= isPropertySetAndNonEmpty(properties, "openapi.version");
        }

        if (openApiEnabled) {
            if (!openApiNamePropertySet) {
                addError("Property \"openapi.name\" is missing or empty in application properties/YAML");
            }

            if (!openApiTitlePropertySet) {
                addError("Property \"openapi.title\" is missing or empty in application*.properties/YAML");
            }

            if (!openApiVersionPropertySet) {
                addError("Property \"openapi.version\" is missing or empty in application*.properties/YAML");
            }
        }
    }

    /**
     * Checks if the given properties contains a property with the given name, and that it isn't
     * blank.
     *
     * @param properties the properties
     * @param name the name of the property to check
     * @return whether the property exists and isn't blank
     */
    private boolean isPropertySetAndNonEmpty(final Properties properties, final String name) {
        return ofNullable(properties.get(name))
            .map(Object::toString)
            .map(value -> !value.isBlank())
            .orElse(false);
    }

    /**
     * Flattens the given map to use dot notation for keys.
     *
     * @param map the map to flatten
     * @return the resulting map
     */
    private Map<String, Object> flatten(final Map<String, Object> map) {
        return map.entrySet().stream()
            .flatMap(entry -> flatten(entry).entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Helper method for flattening maps to use dot notation for keys.
     *
     * @param entry the map entry to process
     * @return the resulting map
     */
    private Map<String, Object> flatten(final Map.Entry<String, Object> entry) {
        if (!(entry.getValue() instanceof Map)) {
            return Map.of(entry.getKey(), ofNullable(entry.getValue()).orElse(""));
        }

        var prefix = entry.getKey();
        @SuppressWarnings("unchecked")
        var values = (Map<String, Object>) entry.getValue();
        // create a new Map, with prefix added to each key
        var flattenMap = new HashMap<String, Object>();
        values.keySet().forEach(key -> flattenMap.put(prefix + "." + key, values.get(key)));

        return flatten(flattenMap);
    }

    private Properties loadProperties(final File propertyFile) throws MojoFailureException {
        var properties = new Properties();
        try {
            if (propertyFile.getName().matches(YAML_FILE_EXTENSION_PATTERN)) {
                // YAML file
                var props = OBJECT_MAPPER.readValue(propertyFile, new TypeReference<Map<String, Object>>() {});

                properties.putAll(flatten(props));
            } else {
                // Properties file
                try (var in = new FileInputStream(propertyFile)) {
                    properties.load(in);
                }
            }

            return properties;
        } catch (IOException e) {
            throw new MojoFailureException(String.format(
                "Unable to load properties/YAML file %s: %s", propertyFile.getName(), e.getMessage()), e);
        }
    }
}
