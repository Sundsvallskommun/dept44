package se.sundsvall.dept44.logbook.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidConfigurationExceptionTest {

	@Test
	void testInvalidConfigurationExceptionWithMessageAndCause() {
		final var cause = new IllegalArgumentException("test");
		final var message = "message";
		final var exception = new InvalidConfigurationException(message, cause);

		assertThat(exception).isInstanceOf(RuntimeException.class);
		assertThat(exception.getMessage()).isEqualTo(message);
		assertThat(exception.getCause()).isSameAs(cause);
	}

	@Test
	void testInvalidConfigurationExceptionWithMessage() {
		final var message = "message";
		final var exception = new InvalidConfigurationException(message);

		assertThat(exception).isInstanceOf(RuntimeException.class);
		assertThat(exception.getMessage()).isEqualTo(message);
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void testInvalidConfigurationExceptionWithCause() {
		final var cause = new IllegalArgumentException("test");
		final var exception = new InvalidConfigurationException(cause);

		assertThat(exception).isInstanceOf(RuntimeException.class);
		assertThat(exception.getMessage()).isEqualTo("java.lang.IllegalArgumentException: test");
		assertThat(exception.getCause()).isSameAs(cause);
	}
}
