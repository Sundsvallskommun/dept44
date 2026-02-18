package se.sundsvall.dept44.common.validators.annotation.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncompatibleAnnotationExceptionTest {

	@Test
	void testIncompatibleAnnotationException() {
		var message = "message";
		assertThat(new IncompatibleAnnotationException(message)).hasMessage(message);
	}
}
