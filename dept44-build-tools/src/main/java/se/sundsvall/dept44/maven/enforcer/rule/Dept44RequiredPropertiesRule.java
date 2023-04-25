package se.sundsvall.dept44.maven.enforcer.rule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.yaml.snakeyaml.error.YAMLException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException;


public class Dept44RequiredPropertiesRule implements EnforcerRule {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

	private static final String DOCKER_IMAGE_NAME_REGEX = "^ms-[a-z0-9-]+$";
	private static final String PROPERTY_FILE_PATTERN = "^application(-\\w+)?(.yaml|.yml|.properties)$";
	private static final String YAML_FILE_EXTENSION_PATTERN = ".*(.yaml|.yml)$";

	private boolean requireDockerImageName = true;

	@Override
	public void execute(final EnforcerRuleHelper helper) throws EnforcerRuleException {
		try {
			var project = (MavenProject) helper.evaluate("${project}");
			var resourcesDir = (String) helper.evaluate("${project.basedir}/src/main/resources");

			// Quick and dirty "workaround" to pass on dept44 framework stuff...
			if ("pom".equalsIgnoreCase(project.getPackaging())) {
				return;
			}

			// Ensure that "docker.image.name" meets requirements
			var errors = ensureDockerImageNameMeetsRequirements(project);

			// Ensure that OpenAPI properties are set
			errors.addAll(ensureOpenApiPropertiesAreSet(resourcesDir));

			if (!errors.isEmpty()) {
				var errorsString = errors.stream().collect(Collectors.joining(System.lineSeparator() + " - ", " - ", System.lineSeparator()));

				throw new EnforcerRuleException(System.lineSeparator() + errorsString);
			}
		} catch (ExpressionEvaluationException e) {
			throw new EnforcerRuleException("Unable to lookup an expression " + e.getLocalizedMessage(), e);
		}
	}

	private List<String> ensureDockerImageNameMeetsRequirements(final MavenProject project) {
		List<String> errors = new ArrayList<>();

		// Ensure that "docker.image.name" is set
		var dockerImageName = project.getProperties().getProperty("docker.image.name", "");
		if (requireDockerImageName && StringUtils.isBlank(dockerImageName)) {
			errors.add("Required build property \"docker.image.name\" is missing or empty");
		}

		// Ensure that "docker.image.name" has proper format
		if (!"".equals(dockerImageName) && !dockerImageName.matches(DOCKER_IMAGE_NAME_REGEX)) {
			errors.add("Build property \"docker.image.name\" must match regex \"" + DOCKER_IMAGE_NAME_REGEX + "\", e.g. \"ms-service-123\"");
		}

		return errors;
	}

	@SuppressFBWarnings("PATH_TRAVERSAL_IN")
	private List<String> ensureOpenApiPropertiesAreSet(final String resourcesDir) throws EnforcerRuleException {
		var openApiEnabled = true;
		var openApiNamePropertySet = false;
		var openApiTitlePropertySet = false;
		var openApiVersionPropertySet = false;

		var propertyFiles = new File(resourcesDir)
			.listFiles((dir, name) -> name.matches(PROPERTY_FILE_PATTERN));

		// We should have some files to work with...
		if (propertyFiles == null || propertyFiles.length == 0) {
			throw new EnforcerRuleException("No application properties/YAML files exist");
		}

		for (File propertyFile : propertyFiles) {
			var properties = new Properties();

			try {
				if (propertyFile.getName().matches(YAML_FILE_EXTENSION_PATTERN)) {
					// YAML file
					var props = OBJECT_MAPPER.readValue(propertyFile,
						new TypeReference<Map<String, Object>>() {});

					properties.putAll(flatten(props));
				} else {
					// Properties file
					try (var in = new FileInputStream(propertyFile)) {
						properties.load(in);
					}
				}
			} catch (IOException e) {
				throw new EnforcerRuleException("Unable to load properties/YAML file " + propertyFile.getName() + ": " + e.getMessage(), e);
			}

			openApiEnabled = Optional.ofNullable(properties.getOrDefault("openapi.enabled", "true"))
				.map(Object::toString)
				.map(Boolean::valueOf)
				.orElse(true);

			// If OpenAPI isn't enabled there's no need to check for the other properties
			if (!openApiEnabled) {
				break;
			}

			if (Optional.ofNullable(properties.get("openapi.name")).isPresent()) {
				openApiNamePropertySet = true;
			}
			if (Optional.ofNullable(properties.get("openapi.title")).isPresent()) {
				openApiTitlePropertySet = true;
			}
			if (Optional.ofNullable(properties.get("openapi.version")).isPresent()) {
				openApiVersionPropertySet = true;
			}
		}

		List<String> errors = new ArrayList<>();
		if (openApiEnabled) {
			if (!openApiNamePropertySet) {
				errors.add("Property \"openapi.name\" is missing or empty in application properties/YAML");
			}

			if (!openApiTitlePropertySet) {
				errors.add("Property \"openapi.title\" is missing or empty in application*.properties/YAML");
			}

			if (!openApiVersionPropertySet) {
				errors.add("Property \"openapi.version\" is missing or empty in application*.properties/YAML");
			}
		}

		return errors;
	}

	/**
	 * If your rule is cacheable, you must return a unique id when parameters or conditions
	 * change that would cause the result to be different. Multiple cached results are stored
	 * based on their id.
	 *
	 * The easiest way to do this is to return a hash computed from the values of your parameters.
	 *
	 * If your rule is not cacheable, then the result here is not important, you may return anything.
	 */
	@Override
	public String getCacheId() {
		return UUID.randomUUID().toString();
	}

	/**
	 * This tells the system if the results are cacheable at all. Keep in mind that during
	 * forked builds and other things, a given rule may be executed more than once for the same
	 * project. This means that even things that change from project to project may still
	 * be cacheable in certain instances.
	 */
	@Override
	public boolean isCacheable() {
		return false;
	}

	/**
	 * If the rule is cacheable and the same id is found in the cache, the stored results
	 * are passed to this method to allow double checking of the results. Most of the time
	 * this can be done by generating unique ids, but sometimes the results of objects returned
	 * by the helper need to be queried. You may for example, store certain objects in your rule
	 * and then query them later.
	 */
	@Override
	public boolean isResultValid(@Nonnull final EnforcerRule rule) {
		return false;
	}

	/**
	 * Sets whether the rule should require {@code docker.image.name} to be set in pom.xml.
	 *
	 * @param requireDockerImageName the value to set
	 */
	public void setRequireDockerImageName(final boolean requireDockerImageName) {
		this.requireDockerImageName = requireDockerImageName;
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
			return Map.of(entry.getKey(), entry.getValue());
		}

		var prefix = entry.getKey();
		@SuppressWarnings("unchecked")
		var values = (Map<String, Object>) entry.getValue();
		// create a new Map, with prefix added to each key
		var flattenMap = new HashMap<String, Object>();
		values.keySet().forEach(key -> flattenMap.put(prefix + "." + key, values.get(key)));

		return flatten(flattenMap);
	}
}
