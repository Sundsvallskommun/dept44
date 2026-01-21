package se.sundsvall.dept44.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED;
import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblemResponse;
import se.sundsvall.dept44.problem.violations.Violation;

class ProblemExceptionHandlerTest {

	private final ProblemExceptionHandler handler = new ProblemExceptionHandler();

	@Test
	void handleConstraintViolationException() {
		// Arrange
		final var violation1 = createConstraintViolation("field1", "must not be null");
		final var violation2 = createConstraintViolation("field2", "must be positive");
		final var exception = new ConstraintViolationException("Validation failed", Set.of(violation1, violation2));

		// Act
		final var response = handler.handleConstraintViolationException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Constraint Violation");
		assertThat(problem.getViolations()).hasSize(2);
		assertThat(problem.getViolations())
			.extracting(Violation::field)
			.containsExactlyInAnyOrder("field1", "field2");
		assertThat(problem.getViolations())
			.extracting(Violation::message)
			.containsExactlyInAnyOrder("must not be null", "must be positive");
	}

	@Test
	void handleConstraintViolationExceptionWithEmptyViolations() {
		// Arrange
		final var exception = new ConstraintViolationException("Validation failed", Set.of());

		// Act
		final var response = handler.handleConstraintViolationException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getViolations()).isEmpty();
	}

	@Test
	void handleMethodArgumentNotValidException() {
		// Arrange
		final var bindingResult = mock(BindingResult.class);
		final var fieldError1 = new FieldError("object", "name", "must not be blank");
		final var fieldError2 = new FieldError("object", "age", "must be greater than 0");
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

		final var exception = new MethodArgumentNotValidException(null, bindingResult);

		// Act
		final var response = handler.handleMethodArgumentNotValidException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Constraint Violation");
		assertThat(problem.getViolations()).hasSize(2);
		assertThat(problem.getViolations())
			.extracting(Violation::field)
			.containsExactly("name", "age");
		assertThat(problem.getViolations())
			.extracting(Violation::message)
			.containsExactly("must not be blank", "must be greater than 0");
	}

	@Test
	void handleThrowableProblem() {
		// Arrange
		final var problem = Problem.valueOf(Status.NOT_FOUND, "Resource not found");

		// Act
		final var response = handler.handleThrowableProblem(problem);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.getStatus()).isEqualTo(Status.NOT_FOUND);
		assertThat(body.getTitle()).isEqualTo("Not Found");
		assertThat(body.getDetail()).isEqualTo("Resource not found");
	}

	@Test
	void handleThrowableProblemWithNullStatus() {
		// Arrange
		final var problem = new ThrowableProblem(null, "Error", (StatusType) null, "Something went wrong", null);

		// Act
		final var response = handler.handleThrowableProblem(problem);

		// Assert
		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void handleConstraintViolationProblem() {
		// Arrange
		final var violations = List.of(
			new Violation("email", "must be a valid email"),
			new Violation("phone", "must match pattern"));

		final var problem = ConstraintViolationProblem.builder()
			.withStatus(Status.BAD_REQUEST)
			.withViolations(violations)
			.build();

		// Act
		final var response = handler.handleThrowableProblem(problem);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getBody()).isInstanceOf(ConstraintViolationProblemResponse.class);

		final var body = (ConstraintViolationProblemResponse) response.getBody();
		assertThat(body.getViolations()).hasSize(2);
	}

	@Test
	void handleMissingServletRequestParameterException() {
		// Arrange
		final var exception = new MissingServletRequestParameterException("target", "String");

		// Act
		final var response = handler.handleMissingServletRequestParameterException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Required request parameter 'target' for method parameter type String is not present");
	}

	@Test
	void handleMissingPathVariableException() throws NoSuchMethodException {
		// Arrange
		final var methodParameter = new MethodParameter(
			ProblemExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class), 0);
		final var exception = new MissingPathVariableException("userId", methodParameter);

		// Act
		final var response = handler.handleMissingPathVariableException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Missing URI template variable 'userId' for method parameter type String");
	}

	@Test
	void handleHttpRequestMethodNotSupportedException() {
		// Arrange
		final var exception = new HttpRequestMethodNotSupportedException("POST", List.of("GET", "PUT"));

		// Act
		final var response = handler.handleHttpRequestMethodNotSupportedException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(METHOD_NOT_ALLOWED);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.METHOD_NOT_ALLOWED);
		assertThat(problem.getTitle()).isEqualTo("Method Not Allowed");
		assertThat(problem.getDetail()).contains("POST");
	}

	@Test
	void handleHttpMediaTypeNotSupportedException() {
		// Arrange
		final var exception = new HttpMediaTypeNotSupportedException(
			MediaType.TEXT_PLAIN, List.of(APPLICATION_JSON));

		// Act
		final var response = handler.handleHttpMediaTypeNotSupportedException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.UNSUPPORTED_MEDIA_TYPE);
		assertThat(problem.getTitle()).isEqualTo("Unsupported Media Type");
	}

	@Test
	void handleHttpMediaTypeNotAcceptableException() {
		// Arrange
		final var exception = new HttpMediaTypeNotAcceptableException(List.of(APPLICATION_JSON));

		// Act
		final var response = handler.handleHttpMediaTypeNotAcceptableException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(NOT_ACCEPTABLE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.NOT_ACCEPTABLE);
		assertThat(problem.getTitle()).isEqualTo("Not Acceptable");
	}

	@Test
	void handleMethodArgumentTypeMismatchException() {
		// Arrange
		final var exception = new MethodArgumentTypeMismatchException(
			"abc", Integer.class, "id", null, new NumberFormatException());

		// Act
		final var response = handler.handleMethodArgumentTypeMismatchException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).contains("id").contains("Integer");
	}

	@Test
	void handleMethodArgumentTypeMismatchExceptionWithNullValue() {
		// Arrange
		final var exception = new MethodArgumentTypeMismatchException(
			null, Integer.class, "id", null, null);

		// Act
		final var response = handler.handleMethodArgumentTypeMismatchException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getDetail()).contains("null");
	}

	@Test
	void handleMethodArgumentTypeMismatchExceptionWithNullRequiredType() {
		// Arrange
		final var exception = new MethodArgumentTypeMismatchException(
			"abc", null, "id", null, null);

		// Act
		final var response = handler.handleMethodArgumentTypeMismatchException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getDetail()).contains("unknown");
	}

	@Test
	void handleTypeMismatchException() {
		// Arrange
		final var exception = new TypeMismatchException("abc", Integer.class);

		// Act
		final var response = handler.handleTypeMismatchException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).contains("Integer");
	}

	@Test
	void handleBindException() {
		// Arrange
		final var target = new Object();
		final var exception = new BindException(target, "target");
		exception.addError(new FieldError("target", "name", "must not be empty"));
		exception.addError(new FieldError("target", "value", "must be positive"));

		// Act
		final var response = handler.handleBindException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Constraint Violation");
		assertThat(problem.getViolations()).hasSize(2);
		assertThat(problem.getViolations())
			.extracting(Violation::field)
			.containsExactlyInAnyOrder("name", "value");
	}

	@Test
	void handleHttpMessageNotReadableException() {
		// Arrange
		final var exception = new HttpMessageNotReadableException("Could not read JSON", null);

		// Act
		final var response = handler.handleHttpMessageNotReadableException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Required request body is missing or malformed");
	}

	@Test
	void handleHttpMessageNotWritableException() {
		// Arrange
		final var exception = new HttpMessageNotWritableException("Could not write response");

		// Act
		final var response = handler.handleHttpMessageNotWritableException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
		assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
		assertThat(problem.getDetail()).isEqualTo("Unable to write response body");
	}

	@Test
	void handleNoHandlerFoundException() {
		// Arrange
		final var exception = new NoHandlerFoundException("GET", "/api/unknown", null);

		// Act
		final var response = handler.handleNoHandlerFoundException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("No handler found for GET /api/unknown");
	}

	@Test
	void handleNoResourceFoundException() {
		// Arrange
		final var exception = new NoResourceFoundException(GET, "static/missing.css", null);

		// Act
		final var response = handler.handleNoResourceFoundException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
	}

	@Test
	void handleMissingServletRequestPartException() {
		// Arrange
		final var exception = new MissingServletRequestPartException("file");

		// Act
		final var response = handler.handleMissingServletRequestPartException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Required request part 'file' is not present");
	}

	@Test
	void handleMissingRequestHeaderException() throws NoSuchMethodException {
		// Arrange
		final var methodParameter = new MethodParameter(
			ProblemExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class), 0);
		final var exception = new MissingRequestHeaderException("X-Custom-Header", methodParameter);

		// Act
		final var response = handler.handleMissingRequestHeaderException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Required request header 'X-Custom-Header' is not present");
	}

	@Test
	void handleMissingRequestCookieException() throws NoSuchMethodException {
		// Arrange
		final var methodParameter = new MethodParameter(
			ProblemExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class), 0);
		final var exception = new MissingRequestCookieException("sessionId", methodParameter);

		// Act
		final var response = handler.handleMissingRequestCookieException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Required request cookie 'sessionId' is not present");
	}

	@Test
	void handleServletRequestBindingException() {
		// Arrange
		final var exception = new ServletRequestBindingException("Binding failed");

		// Act
		final var response = handler.handleServletRequestBindingException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Binding failed");
	}

	@Test
	void handleResponseStatusException() {
		// Arrange
		final var exception = new ResponseStatusException(NOT_FOUND, "Resource not found");

		// Act
		final var response = handler.handleResponseStatusException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("Resource not found");
	}

	@Test
	void handleAsyncRequestTimeoutException() {
		// Arrange
		final var exception = new AsyncRequestTimeoutException();

		// Act
		final var response = handler.handleAsyncRequestTimeoutException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.SERVICE_UNAVAILABLE);
		assertThat(problem.getTitle()).isEqualTo("Service Unavailable");
		assertThat(problem.getDetail()).isEqualTo("Async request timed out");
	}

	@Test
	void handleAccessDeniedException() {
		// Arrange
		final var exception = new AccessDeniedException("Access is denied");

		// Act
		final var response = handler.handleAccessDeniedException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.FORBIDDEN);
		assertThat(problem.getTitle()).isEqualTo("Forbidden");
		assertThat(problem.getDetail()).isEqualTo("Access is denied");
	}

	@Test
	void handleAuthenticationException() {
		// Arrange
		final var exception = new BadCredentialsException("Bad credentials");

		// Act
		final var response = handler.handleAuthenticationException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.UNAUTHORIZED);
		assertThat(problem.getTitle()).isEqualTo("Unauthorized");
		assertThat(problem.getDetail()).isEqualTo("Bad credentials");
	}

	@Test
	void handleUnsupportedOperationException() {
		// Arrange
		final var exception = new UnsupportedOperationException("This operation is not supported");

		// Act
		final var response = handler.handleUnsupportedOperationException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(NOT_IMPLEMENTED);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(Status.NOT_IMPLEMENTED);
		assertThat(problem.getTitle()).isEqualTo("Not Implemented");
		assertThat(problem.getDetail()).isEqualTo("This operation is not supported");
	}

	// Helper method for MissingPathVariableException test
	void sampleMethod(final String param) {
		// This method exists only to provide a MethodParameter for testing
	}

	@SuppressWarnings("unchecked")
	private ConstraintViolation<Object> createConstraintViolation(final String propertyPath, final String message) {
		final var violation = mock(ConstraintViolation.class);
		final var path = mock(Path.class);
		when(path.toString()).thenReturn(propertyPath);
		when(violation.getPropertyPath()).thenReturn(path);
		when(violation.getMessage()).thenReturn(message);
		return violation;
	}
}
