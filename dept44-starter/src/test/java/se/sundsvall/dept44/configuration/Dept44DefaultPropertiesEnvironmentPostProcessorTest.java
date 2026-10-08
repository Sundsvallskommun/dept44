package se.sundsvall.dept44.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class Dept44DefaultPropertiesEnvironmentPostProcessorTest {

	private final Dept44DefaultPropertiesEnvironmentPostProcessor postProcessor = new Dept44DefaultPropertiesEnvironmentPostProcessor();

	@Test
	void defaultsHaveTheLowestPrecedence() {
		final var environment = new StandardEnvironment();
		environment.getPropertySources().addLast(new MapPropertySource("application", Map.of("spring.datasource.hikari.maximum-pool-size", "50")));

		postProcessor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("50");
		assertThat(environment.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("3");
		assertThat(environment.getProperty("springdoc.swagger-ui.url")).isEqualTo("/api-docs");
		assertThat(environment.getPropertySources().stream().toList().getLast().getName()).isEqualTo("dept44Defaults");
	}

	@Test
	void defaultsAreAddedOnce() {
		final var environment = new StandardEnvironment();

		postProcessor.postProcessEnvironment(environment, new SpringApplication());
		postProcessor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getPropertySources().stream().filter(source -> source.getName().equals("dept44Defaults"))).hasSize(1);
	}

	@Test
	void missingDefaultsFailTheStartup() {
		final var environment = new StandardEnvironment();

		assertThatExceptionOfType(UncheckedIOException.class)
			.isThrownBy(() -> Dept44DefaultPropertiesEnvironmentPostProcessor.addDefaults(environment, "no-such-defaults.properties"))
			.withMessage("Could not load no-such-defaults.properties");
	}

	@Test
	void runsAfterTheApplicationConfigurationIsLoaded() {
		assertThat(postProcessor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
	}

	/**
	 * A service's root application configuration next to dept44's {@code config/application.properties}, the location
	 * Spring Boot ranks above it. Defaults kept in that file would win over the service's own value.
	 */
	@Test
	void applicationConfigurationOverridesTheDefaultsInARunningApplication() {
		final var application = new SpringApplication(EmptyConfiguration.class);
		application.setWebApplicationType(WebApplicationType.NONE);

		try (final var context = application.run("--spring.config.location=optional:classpath:/precedence/,optional:classpath:/config/")) {
			final var environment = context.getEnvironment();

			assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("50");
			assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
		}
	}

	@Test
	void dept44ConfigDataFileHoldsOnlyTheEnvImport() throws IOException {
		final var properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("config/application.properties"));

		assertThat(properties).containsOnlyKeys("spring.config.import");
	}

	@Configuration(proxyBeanMethods = false)
	static class EmptyConfiguration {
	}
}
