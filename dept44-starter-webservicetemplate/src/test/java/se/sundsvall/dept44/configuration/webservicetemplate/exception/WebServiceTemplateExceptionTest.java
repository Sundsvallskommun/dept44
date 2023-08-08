package se.sundsvall.dept44.configuration.webservicetemplate.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class WebServiceTemplateExceptionTest {

	@Test
	void testWebServiceTemplateException() {
		var message = "message";
		assertThat(new WebserviceTemplateException(message)).hasMessage(message);
	}

	@Test
	void testWebServiceTemplateExceptionWithCause() {
		var message = "message";
		var cause = new RuntimeException();
		assertThat(new WebserviceTemplateException(message, cause)).hasMessage(message).hasCause(cause);
	}
}
