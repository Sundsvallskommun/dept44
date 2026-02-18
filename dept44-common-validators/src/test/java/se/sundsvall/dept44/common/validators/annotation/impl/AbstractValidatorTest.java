package se.sundsvall.dept44.common.validators.annotation.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractValidatorTest {

	@Test
	void testAbstractCreateExceptionMethod() {
		assertThat(AbstractValidator.createException("path.to.class").get()).hasMessage("path.to.class does not contain method message()");
	}

	@Test
	void testAbstractCreateExceptionMethodWithNull() {
		final var exception = AbstractValidator.createException(null);

		assertThat(assertThrows(IllegalArgumentException.class, exception::get)).hasMessage("className may not be null");
	}
}
