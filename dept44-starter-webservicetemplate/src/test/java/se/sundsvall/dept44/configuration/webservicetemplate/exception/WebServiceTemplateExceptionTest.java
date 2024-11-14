package se.sundsvall.dept44.configuration.webservicetemplate.exception;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

class WebServiceTemplateExceptionTest {

	@Test
	void testWebServiceTemplateException() {
		var message = "message";
		assertThat(new WebServiceTemplateException(message)).hasMessage(message);
	}

	@Test
	void testWebServiceTemplateExceptionWithCause() {
		var message = "message";
		var cause = new RuntimeException();
		assertThat(new WebServiceTemplateException(message, cause)).hasMessage(message).hasCause(cause);
	}
}
