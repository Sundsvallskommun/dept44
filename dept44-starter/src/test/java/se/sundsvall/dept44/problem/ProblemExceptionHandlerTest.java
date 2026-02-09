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

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblemResponse;
import se.sundsvall.dept44.problem.violations.Violation;

class ProblemExceptionHandlerTest {

	private final ProblemExceptionHandler handler = new ProblemExceptionHandler();
	private final ServletWebRequest webRequest = mock(ServletWebRequest.class);

	@Test
	void handleConstraintViolationException() {
		final var violation1 = createConstraintViolation("field1", "must not be null");
		final var violation2 = createConstraintViolation("field2", "must be positive");
		final var exception = new ConstraintViolationException("Validation failed", Set.of(violation1, violation2));

		final var response = handler.handleConstraintViolationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
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
		final var exception = new ConstraintViolationException("Validation failed", Set.of());

		final var response = handler.handleConstraintViolationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getViolations()).isEmpty();
	}

	@Test
	void handleMethodArgumentNotValidException() {
		final var bindingResult = mock(BindingResult.class);
		final var fieldError1 = new FieldError("object", "name", "must not be blank");
		final var fieldError2 = new FieldError("object", "age", "must be greater than 0");
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

		final var exception = new MethodArgumentNotValidException(null, bindingResult);

		final var response = handler.handleMethodArgumentNotValid(exception, new HttpHeaders(), BAD_REQUEST, webRequest);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var body = response.getBody();
		assertThat(body).isInstanceOf(ConstraintViolationProblem.class);
		final var problem = (ConstraintViolationProblem) body;
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
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
	void handleCallNotPermittedException() {
		// Arrange
		final var exception = mock(CallNotPermittedException.class);
		when(exception.getMessage()).thenReturn("CircuitBreaker 'petstore' is OPEN");

		// Act
		final var response = handler.handleCallNotPermittedException(exception);

		// Assert
		assertThat(response.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.getStatus()).isEqualTo(Status.SERVICE_UNAVAILABLE);
		assertThat(body.getTitle()).isEqualTo("Service Unavailable");
		assertThat(body.getDetail()).isEqualTo("CircuitBreaker 'petstore' is OPEN");
	}

	@Test
	void handleThrowableProblem() {
		final var problem = Problem.valueOf(NOT_FOUND, "Resource not found");

		final var response = handler.handleExceptionInternal(problem, null, new HttpHeaders(), HttpStatusCode.valueOf(404), webRequest);

		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var body = (Problem) response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(body.getTitle()).isEqualTo("Not Found");
		assertThat(body.getDetail()).isEqualTo("Resource not found");
	}

	@Test
	void handleThrowableProblemWithNullStatus() {
		final var problem = new ThrowableProblem(null, "Error", (HttpStatus) null, "Something went wrong", null);

		final var response = handler.handleExceptionInternal(problem, null, new HttpHeaders(), HttpStatusCode.valueOf(500), webRequest);

		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void handleConstraintViolationProblem() {
		final var violations = List.of(
			new Violation("email", "must be a valid email"),
			new Violation("phone", "must match pattern"));

		final var problem = ConstraintViolationProblem.builder()
			.withStatus(BAD_REQUEST)
			.withViolations(violations)
			.build();

		final var response = handler.handleExceptionInternal(problem, null, new HttpHeaders(), HttpStatusCode.valueOf(400), webRequest);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getBody()).isInstanceOf(ConstraintViolationProblemResponse.class);

		final var body = (ConstraintViolationProblemResponse) response.getBody();
		assertThat(body.getViolations()).hasSize(2);
	}

	@Test
	void handleMissingServletRequestParameterException() {
		final var exception = new MissingServletRequestParameterException("target", "String");

		final var response = handleInherited(exception, BAD_REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
	}

	@Test
	void handleMissingPathVariableException() throws NoSuchMethodException {
		final var methodParameter = new MethodParameter(
			ProblemExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class), 0);
		final var exception = new MissingPathVariableException("userId", methodParameter);

		final var response = handleInherited(exception, INTERNAL_SERVER_ERROR);

		assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
	}

	@Test
	void handleHttpRequestMethodNotSupportedException() {
		final var exception = new HttpRequestMethodNotSupportedException("POST", List.of("GET", "PUT"));

		final var response = handleInherited(exception, METHOD_NOT_ALLOWED);

		assertThat(response.getStatusCode()).isEqualTo(METHOD_NOT_ALLOWED);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(METHOD_NOT_ALLOWED);
		assertThat(problem.getTitle()).isEqualTo("Method Not Allowed");
	}

	@Test
	void handleHttpMediaTypeNotSupportedException() {
		final var exception = new HttpMediaTypeNotSupportedException(
			MediaType.TEXT_PLAIN, List.of(APPLICATION_JSON));

		final var response = handleInherited(exception, UNSUPPORTED_MEDIA_TYPE);

		assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(UNSUPPORTED_MEDIA_TYPE);
		assertThat(problem.getTitle()).isEqualTo("Unsupported Media Type");
	}

	@Test
	void handleHttpMediaTypeNotAcceptableException() {
		final var exception = new HttpMediaTypeNotAcceptableException(List.of(APPLICATION_JSON));

		final var response = handleInherited(exception, NOT_ACCEPTABLE);

		assertThat(response.getStatusCode()).isEqualTo(NOT_ACCEPTABLE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(NOT_ACCEPTABLE);
		assertThat(problem.getTitle()).isEqualTo("Not Acceptable");
	}

	@Test
	void handleTypeMismatchException() {
		final var exception = new TypeMismatchException("abc", Integer.class);

		final var response = handleInherited(exception, BAD_REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
	}

	@Test
	void handleBindException() {
		final var target = new Object();
		final var exception = new BindException(target, "target");
		exception.addError(new FieldError("target", "name", "must not be empty"));
		exception.addError(new FieldError("target", "value", "must be positive"));

		final var response = handler.handleBindException(exception);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Constraint Violation");
		assertThat(problem.getViolations()).hasSize(2);
		assertThat(problem.getViolations())
			.extracting(Violation::field)
			.containsExactlyInAnyOrder("name", "value");
	}

	@Test
	void handleHttpMessageNotReadableException() {
		final var exception = new HttpMessageNotReadableException("Could not read JSON", null);

		final var response = handleInherited(exception, BAD_REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
	}

	@Test
	void handleHttpMessageNotWritableException() {
		final var exception = new HttpMessageNotWritableException("Could not write response");

		final var response = handleInherited(exception, INTERNAL_SERVER_ERROR);

		assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
	}

	@Test
	void handleNoHandlerFoundException() {
		final var exception = new NoHandlerFoundException("GET", "/api/unknown", null);

		final var response = handleInherited(exception, NOT_FOUND);

		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
	}

	@Test
	void handleNoResourceFoundException() {
		final var exception = new NoResourceFoundException(GET, "static/missing.css", null);

		final var response = handleInherited(exception, NOT_FOUND);

		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
	}

	@Test
	void handleMissingServletRequestPartException() {
		final var exception = new MissingServletRequestPartException("file");

		final var response = handleInherited(exception, BAD_REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
	}

	@Test
	void handleMissingRequestHeaderException() throws NoSuchMethodException {
		final var methodParameter = new MethodParameter(
			ProblemExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class), 0);
		final var exception = new MissingRequestHeaderException("X-Custom-Header", methodParameter);

		final var response = handleInherited(exception, BAD_REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
	}

	@Test
	void handleMissingRequestCookieException() throws NoSuchMethodException {
		final var methodParameter = new MethodParameter(
			ProblemExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class), 0);
		final var exception = new MissingRequestCookieException("sessionId", methodParameter);

		final var response = handleInherited(exception, BAD_REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
	}

	@Test
	void handleServletRequestBindingException() {
		final var exception = new ServletRequestBindingException("Binding failed");

		final var response = handleInherited(exception, BAD_REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
	}

	@Test
	void handleResponseStatusException() {
		final var exception = new ResponseStatusException(NOT_FOUND, "Resource not found");

		final var response = handleInherited(exception, NOT_FOUND);

		assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
	}

	@Test
	void handleAsyncRequestTimeoutException() {
		final var exception = new AsyncRequestTimeoutException();

		final var response = handleInherited(exception, SERVICE_UNAVAILABLE);

		assertThat(response.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = asProblem(response);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(SERVICE_UNAVAILABLE);
		assertThat(problem.getTitle()).isEqualTo("Service Unavailable");
	}

	@Test
	void handleAccessDeniedException() {
		final var exception = new AccessDeniedException("Access is denied");

		final var response = handler.handleAccessDeniedException(exception);

		assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(FORBIDDEN);
		assertThat(problem.getTitle()).isEqualTo("Forbidden");
		assertThat(problem.getDetail()).isEqualTo("Access is denied");
	}

	@Test
	void handleAuthenticationException() {
		final var exception = new BadCredentialsException("Bad credentials");

		final var response = handler.handleAuthenticationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(UNAUTHORIZED);
		assertThat(problem.getTitle()).isEqualTo("Unauthorized");
		assertThat(problem.getDetail()).isEqualTo("Bad credentials");
	}

	@Test
	void handleUnsupportedOperationException() {
		final var exception = new UnsupportedOperationException("This operation is not supported");

		final var response = handler.handleUnsupportedOperationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(NOT_IMPLEMENTED);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var problem = response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(NOT_IMPLEMENTED);
		assertThat(problem.getTitle()).isEqualTo("Not Implemented");
		assertThat(problem.getDetail()).isEqualTo("This operation is not supported");
	}

	@Test
	void handleExceptionInternalConvertsProblemDetailBody() {
		final var pd = ProblemDetail.forStatus(404);
		pd.setTitle("Not Found");
		pd.setDetail("The resource was not found");

		final var response = handler.handleExceptionInternal(
			new RuntimeException(), pd, new HttpHeaders(), HttpStatusCode.valueOf(404), webRequest);

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getHeaders().getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON);

		final var body = response.getBody();
		assertThat(body).isInstanceOf(ProblemResponse.class);
		final var problem = (ProblemResponse) body;
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("The resource was not found");
		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
	}

	@Test
	void handleExceptionInternalPassesThroughNonProblemDetailBody() {
		final var customBody = "custom error body";

		final var response = handler.handleExceptionInternal(
			new RuntimeException(), customBody, new HttpHeaders(), HttpStatusCode.valueOf(500), webRequest);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isEqualTo(customBody);
	}

	// Helper method for MissingPathVariableException test
	void sampleMethod(final String param) {
		// This method exists only to provide a MethodParameter for testing
	}

	/**
	 * Helper to invoke handleExceptionInternal for exceptions that are now handled by the superclass.
	 * Creates a ProblemDetail body matching what Spring's ResponseEntityExceptionHandler would pass.
	 */
	private ResponseEntity<Object> handleInherited(final Exception exception, final HttpStatusCode expectedStatus) {
		final ProblemDetail pd;
		if (exception instanceof org.springframework.web.ErrorResponse errorResponse) {
			pd = errorResponse.getBody();
		} else {
			pd = ProblemDetail.forStatus(expectedStatus);
			pd.setTitle(org.springframework.http.HttpStatus.valueOf(expectedStatus.value()).getReasonPhrase());
			pd.setDetail(exception.getMessage());
		}
		return handler.handleExceptionInternal(exception, pd, new HttpHeaders(), expectedStatus, webRequest);
	}

	private Problem asProblem(final ResponseEntity<Object> response) {
		return (Problem) response.getBody();
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
