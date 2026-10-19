package se.sundsvall.dept44.rabbitmq.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Adds the starter's default properties with the lowest precedence, so that a service's own configuration overrides
 * any of them.
 * <p>
 * Works as {@code Dept44DefaultPropertiesEnvironmentPostProcessor} in dept44-starter, but with a property source of its
 * own: each skips its defaults once its source is present, and a shared name would make one skip the other's.
 */
public class RabbitMqDefaultPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String PROPERTY_SOURCE_NAME = "dept44RabbitMqDefaults";
	static final String DEFAULTS_LOCATION = "dept44-rabbitmq-default.properties";

	@Override
	public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {
		addDefaults(environment, DEFAULTS_LOCATION);
	}

	static void addDefaults(final ConfigurableEnvironment environment, final String location) {
		if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
			return;
		}
		try {
			final var defaults = PropertiesLoaderUtils.loadProperties(new ClassPathResource(location, RabbitMqDefaultPropertiesEnvironmentPostProcessor.class.getClassLoader()));
			environment.getPropertySources().addLast(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, defaults));
		} catch (final IOException e) {
			throw new UncheckedIOException("Could not load " + location, e);
		}
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
