package se.sundsvall.dept44.rabbitmq.config;

import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
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
		assertThat(environment.getProperty("management.health.rabbit.enabled")).isEqualTo("false");
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

	@Test
	void runsAfterTheApplicationConfigurationIsLoaded() {
		assertThat(postProcessor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
	}
}
