package se.sundsvall.dept44.rabbitmq.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RabbitMqDefaultPropertiesEnvironmentPostProcessorTest {

	private final RabbitMqDefaultPropertiesEnvironmentPostProcessor postProcessor = new RabbitMqDefaultPropertiesEnvironmentPostProcessor();

	@Test
	void defaults() {
		final var environment = new StandardEnvironment();

		postProcessor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
		assertThat(environment.getProperty("spring.rabbitmq.publisher-returns")).isEqualTo("true");
		assertThat(environment.getProperty("spring.rabbitmq.template.mandatory")).isEqualTo("true");
		assertThat(environment.getProperty("spring.rabbitmq.connection-timeout")).isEqualTo("5s");
		assertThat(environment.getProperty("spring.rabbitmq.dynamic")).isEqualTo("false");
		assertThat(environment.getProperty("spring.rabbitmq.template.observation-enabled")).isEqualTo("true");
		assertThat(environment.getProperty("spring.rabbitmq.listener.simple.observation-enabled")).isEqualTo("true");
		assertThat(environment.getProperty("spring.rabbitmq.listener.direct.observation-enabled")).isEqualTo("true");
		for (final var container : new String[] {
			"simple", "direct"
		}) {
			assertThat(environment.getProperty("spring.rabbitmq.listener." + container + ".retry.enabled")).isEqualTo("true");
			assertThat(environment.getProperty("spring.rabbitmq.listener." + container + ".retry.initial-interval")).isEqualTo("1s");
			assertThat(environment.getProperty("spring.rabbitmq.listener." + container + ".retry.multiplier")).isEqualTo("2");
			assertThat(environment.getProperty("spring.rabbitmq.listener." + container + ".retry.max-interval")).isEqualTo("30s");
			assertThat(environment.getProperty("spring.rabbitmq.listener." + container + ".retry.max-retries")).isEqualTo("20");
			assertThat(environment.getProperty("spring.rabbitmq.listener." + container + ".prefetch")).isEqualTo("1");
		}
	}

	@Test
	void defaultsHaveTheLowestPrecedence() {
		final var environment = new StandardEnvironment();
		environment.getPropertySources().addLast(new MapPropertySource("application", Map.of("spring.rabbitmq.dynamic", "true")));

		postProcessor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty("spring.rabbitmq.dynamic")).isEqualTo("true");
		assertThat(environment.getProperty("spring.rabbitmq.publisher-returns")).isEqualTo("true");
		assertThat(environment.getPropertySources().stream().toList().getLast().getName()).isEqualTo("dept44RabbitMqDefaults");
	}

	@Test
	void defaultsAreAddedOnce() {
		final var environment = new StandardEnvironment();

		postProcessor.postProcessEnvironment(environment, new SpringApplication());
		postProcessor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getPropertySources().stream().filter(source -> source.getName().equals("dept44RabbitMqDefaults"))).hasSize(1);
	}

	@Test
	void addedNextToTheDept44Defaults() {
		final var environment = new StandardEnvironment();
		environment.getPropertySources().addLast(new MapPropertySource("dept44Defaults", Map.of("server.shutdown", "graceful")));

		postProcessor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
		assertThat(environment.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
	}

	@Test
	void missingDefaultsFailTheStartup() {
		final var environment = new StandardEnvironment();

		assertThatExceptionOfType(UncheckedIOException.class)
			.isThrownBy(() -> RabbitMqDefaultPropertiesEnvironmentPostProcessor.addDefaults(environment, "no-such-defaults.properties"))
			.withMessage("Could not load no-such-defaults.properties");
	}

	/**
	 * Run before ConfigData, the defaults would come before the application's configuration, which ConfigData adds last.
	 */
	@Test
	void runsAfterTheApplicationConfigurationIsLoaded(@TempDir final Path directory) throws IOException {
		final var configuration = Files.writeString(directory.resolve("application.properties"), "spring.rabbitmq.dynamic=true");
		final var application = new SpringApplication(EmptyApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);

		try (final var context = application.run("--spring.config.location=" + configuration.toUri())) {
			// The order rather than a value, which an environment variable such as SPRING_RABBITMQ_DYNAMIC would decide
			final var names = context.getEnvironment().getPropertySources().stream().map(PropertySource::getName).toList();
			final var applicationConfiguration = names.stream().filter(name -> name.contains("application.properties")).findFirst();

			assertThat(applicationConfiguration).isPresent();
			assertThat(names.indexOf("dept44RabbitMqDefaults")).isGreaterThan(names.indexOf(applicationConfiguration.get()));
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class EmptyApplication {
	}
}
