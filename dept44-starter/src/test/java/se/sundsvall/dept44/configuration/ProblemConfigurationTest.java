package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import se.sundsvall.dept44.problem.ProblemExceptionHandler;

class ProblemConfigurationTest {

	@Test
	void hasConfigurationAnnotation() {
		assertThat(ProblemConfiguration.class.getAnnotation(Configuration.class)).isNotNull();
	}

	@Test
	void hasConditionalOnWebApplicationAnnotation() {
		final var annotation = ProblemConfiguration.class.getAnnotation(ConditionalOnWebApplication.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.type()).isEqualTo(ConditionalOnWebApplication.Type.SERVLET);
	}

	@Test
	void importsProblemExceptionHandler() {
		final var annotation = ProblemConfiguration.class.getAnnotation(Import.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).contains(ProblemExceptionHandler.class);
	}

	@Test
	void canBeInstantiated() {
		// This test ensures the configuration class can be instantiated
		// which provides line coverage for the implicit constructor
		final var config = new ProblemConfiguration();
		assertThat(config).isNotNull();
	}
}
