package se.sundsvall.dept44.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Adds dept44's default properties with the lowest precedence, so that a service's own configuration (its
 * {@code application.yml}, profiles, environment variables) overrides any of them.
 * <p>
 * The defaults used to be in {@code classpath:/config/application.properties}, which Spring Boot ranks above a
 * service's
 * {@code classpath:/application.yml}: a service could not override them there. Runs after Spring Boot has loaded the
 * application's config data, since that is added last as well.
 * <p>
 * The dept44 version is kept in its own file, which Maven filters when dept44 is built. The defaults are not filtered,
 * since they contain Spring placeholders that filtering could replace.
 */
public class Dept44DefaultPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String PROPERTY_SOURCE_NAME = "dept44Defaults";
	static final String DEFAULTS_LOCATION = "dept44-default.properties";
	static final String VERSION_LOCATION = "dept44-version.properties";

	@Override
	public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {
		addDefaults(environment, DEFAULTS_LOCATION, VERSION_LOCATION);
	}

	static void addDefaults(final ConfigurableEnvironment environment, final String... locations) {
		if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
			return;
		}
		final var defaults = new Properties();
		for (final var location : locations) {
			try {
				PropertiesLoaderUtils.fillProperties(defaults, new ClassPathResource(location, Dept44DefaultPropertiesEnvironmentPostProcessor.class.getClassLoader()));
			} catch (final IOException e) {
				throw new UncheckedIOException("Could not load " + location, e);
			}
		}
		environment.getPropertySources().addLast(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, defaults));
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
