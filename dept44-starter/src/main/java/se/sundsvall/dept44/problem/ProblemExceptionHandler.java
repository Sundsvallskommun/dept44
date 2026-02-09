package se.sundsvall.dept44.problem;

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
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
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
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

/**
 * Global exception handler for validation exceptions. Converts validation exceptions to RFC 9457 Problem responses.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProblemExceptionHandler {

	private static final String CONSTRAINT_VIOLATION_TITLE = "Constraint Violation";

	/**
	 * Handle Jakarta validation ConstraintViolationException. Typically, thrown when @Validated is used on path/query
	 * parameters.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	@ResponseBody
	public ResponseEntity<ConstraintViolationProblem> handleConstraintViolationException(final ConstraintViolationException exception) {
		final var violations = exception.getConstraintViolations().stream()
			.map(this::toViolation)
			.toList();

		final var problem = ConstraintViolationProblem.builder()
			.withStatus(Status.BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(violations)
			.build();

		return ResponseEntity
			.status(BAD_REQUEST)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	/**
	 * Handle Spring's MethodArgumentNotValidException. Thrown when @Valid validation fails on @RequestBody.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseBody
	public ResponseEntity<ConstraintViolationProblem> handleMethodArgumentNotValidException(final MethodArgumentNotValidException exception) {
		final var violations = exception.getBindingResult().getFieldErrors().stream()
			.map(this::toViolation)
			.toList();

		final var problem = ConstraintViolationProblem.builder()
			.withStatus(Status.BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(violations)
			.build();

		return ResponseEntity
			.status(BAD_REQUEST)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	/**
	 * Handle Resilience4j CallNotPermittedException. Thrown when a circuit breaker is open and rejects the call.
	 */
	@ExceptionHandler(CallNotPermittedException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleCallNotPermittedException(final CallNotPermittedException exception) {
		final var problem = Problem.valueOf(Status.SERVICE_UNAVAILABLE, exception.getMessage());

		return ResponseEntity
			.status(SERVICE_UNAVAILABLE)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(ProblemResponse.from(problem));
	}

	/**
	 * Handle ThrowableProblem exceptions directly. This ensures a consistent Problem response format.
	 */
	@ExceptionHandler(ThrowableProblem.class)
	@ResponseBody
	public ResponseEntity<Problem> handleThrowableProblem(final ThrowableProblem problem) {
		final var status = problem.getStatus() != null
			? problem.getStatus().getStatusCode()
			: 500;

		// Use ProblemResponse to avoid Jackson serialization issues with Throwable's cyclic reference
		final var responseProblem = ProblemResponse.from(problem);

		return ResponseEntity
			.status(status)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(responseProblem);
	}

	/**
	 * Handle MissingServletRequestParameterException. Thrown when a required request parameter is missing.
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleMissingServletRequestParameterException(final MissingServletRequestParameterException exception) {
		final var detail = String.format("Required request parameter '%s' for method parameter type %s is not present",
			exception.getParameterName(), exception.getParameterType());

		return createBadRequestProblem(detail);
	}

	/**
	 * Handle MissingPathVariableException. Thrown when a required path variable is missing.
	 */
	@ExceptionHandler(MissingPathVariableException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleMissingPathVariableException(final MissingPathVariableException exception) {
		final var detail = String.format("Missing URI template variable '%s' for method parameter type %s",
			exception.getVariableName(), exception.getParameter().getParameterType().getSimpleName());

		return createBadRequestProblem(detail);
	}

	/**
	 * Handle HttpRequestMethodNotSupportedException. Thrown when an HTTP method is not supported.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleHttpRequestMethodNotSupportedException(final HttpRequestMethodNotSupportedException exception) {
		final var problem = new ProblemResponse();
		problem.setStatus(Status.METHOD_NOT_ALLOWED);
		problem.setTitle(METHOD_NOT_ALLOWED.getReasonPhrase());
		problem.setDetail(exception.getMessage());

		return ResponseEntity
			.status(METHOD_NOT_ALLOWED)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	/**
	 * Handle HttpMediaTypeNotSupportedException. Thrown when a Content-Type is not supported.
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleHttpMediaTypeNotSupportedException(final HttpMediaTypeNotSupportedException exception) {
		final var problem = new ProblemResponse();
		problem.setStatus(Status.UNSUPPORTED_MEDIA_TYPE);
		problem.setTitle(UNSUPPORTED_MEDIA_TYPE.getReasonPhrase());
		problem.setDetail(exception.getMessage());

		return ResponseEntity
			.status(UNSUPPORTED_MEDIA_TYPE)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	/**
	 * Handle HttpMediaTypeNotAcceptableException. Thrown when a requested media type is not acceptable.
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleHttpMediaTypeNotAcceptableException(final HttpMediaTypeNotAcceptableException exception) {
		final var problem = new ProblemResponse();
		problem.setStatus(Status.NOT_ACCEPTABLE);
		problem.setTitle(NOT_ACCEPTABLE.getReasonPhrase());
		problem.setDetail(exception.getMessage());

		return ResponseEntity
			.status(NOT_ACCEPTABLE)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	/**
	 * Handle MethodArgumentTypeMismatchException. Thrown when a method argument is not the expected type.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleMethodArgumentTypeMismatchException(final MethodArgumentTypeMismatchException exception) {
		final var requiredType = exception.getRequiredType();
		final var typeName = requiredType != null ? requiredType.getSimpleName() : "unknown";
		final var detail = String.format("Failed to convert value of type '%s' to required type '%s' for parameter '%s'",
			exception.getValue() != null ? exception.getValue().getClass().getSimpleName() : "null",
			typeName,
			exception.getName());

		return createBadRequestProblem(detail);
	}

	/**
	 * Handle TypeMismatchException. Thrown when a type conversion fails.
	 */
	@ExceptionHandler(TypeMismatchException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleTypeMismatchException(final TypeMismatchException exception) {
		final var requiredType = exception.getRequiredType();
		final var typeName = requiredType != null ? requiredType.getSimpleName() : "unknown";
		final var detail = String.format("Failed to convert value of type '%s' to required type '%s'",
			exception.getValue() != null ? exception.getValue().getClass().getSimpleName() : "null",
			typeName);

		return createBadRequestProblem(detail);
	}

	/**
	 * Handle BindException. Thrown when binding errors occur during form submission.
	 */
	@ExceptionHandler(BindException.class)
	@ResponseBody
	public ResponseEntity<ConstraintViolationProblem> handleBindException(final BindException exception) {
		final var violations = exception.getBindingResult().getFieldErrors().stream()
			.map(this::toViolation)
			.toList();

		final var problem = ConstraintViolationProblem.builder()
			.withStatus(Status.BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(violations)
			.build();

		return ResponseEntity
			.status(BAD_REQUEST)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	/**
	 * Handle HttpMessageNotReadableException. Thrown when the request body cannot be read (e.g., malformed JSON).
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleHttpMessageNotReadableException(final HttpMessageNotReadableException exception) {
		return createBadRequestProblem("Required request body is missing or malformed");
	}

	/**
	 * Handle HttpMessageNotWritableException. Thrown when the response body cannot be written.
	 */
	@ExceptionHandler(HttpMessageNotWritableException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleHttpMessageNotWritableException(final HttpMessageNotWritableException exception) {
		return createProblem(INTERNAL_SERVER_ERROR, "Unable to write response body");
	}

	/**
	 * Handle NoHandlerFoundException. Thrown when no handler is found for a request.
	 */
	@ExceptionHandler(NoHandlerFoundException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleNoHandlerFoundException(final NoHandlerFoundException exception) {
		final var detail = String.format("No handler found for %s %s", exception.getHttpMethod(), exception.getRequestURL());
		return createProblem(NOT_FOUND, detail);
	}

	/**
	 * Handle NoResourceFoundException. Thrown when a static resource is not found (Spring 6.1+).
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleNoResourceFoundException(final NoResourceFoundException exception) {
		return createProblem(NOT_FOUND, exception.getMessage());
	}

	/**
	 * Handle MissingServletRequestPartException. Thrown when a required multipart file is missing.
	 */
	@ExceptionHandler(MissingServletRequestPartException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleMissingServletRequestPartException(final MissingServletRequestPartException exception) {
		final var detail = String.format("Required request part '%s' is not present", exception.getRequestPartName());
		return createBadRequestProblem(detail);
	}

	/**
	 * Handle MissingRequestHeaderException. Thrown when a required request header is missing.
	 */
	@ExceptionHandler(MissingRequestHeaderException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleMissingRequestHeaderException(final MissingRequestHeaderException exception) {
		final var detail = String.format("Required request header '%s' is not present", exception.getHeaderName());
		return createBadRequestProblem(detail);
	}

	/**
	 * Handle MissingRequestCookieException. Thrown when a required request cookie is missing.
	 */
	@ExceptionHandler(MissingRequestCookieException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleMissingRequestCookieException(final MissingRequestCookieException exception) {
		final var detail = String.format("Required request cookie '%s' is not present", exception.getCookieName());
		return createBadRequestProblem(detail);
	}

	/**
	 * Handle ServletRequestBindingException. General exception for request binding failures.
	 */
	@ExceptionHandler(ServletRequestBindingException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleServletRequestBindingException(final ServletRequestBindingException exception) {
		return createBadRequestProblem(exception.getMessage());
	}

	/**
	 * Handle ResponseStatusException. Generic Spring exception for returning a specific status.
	 */
	@ExceptionHandler(ResponseStatusException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleResponseStatusException(final ResponseStatusException exception) {
		final var httpStatus = HttpStatus.valueOf(exception.getStatusCode().value());
		return createProblem(httpStatus, exception.getReason());
	}

	/**
	 * Handle AsyncRequestTimeoutException. Thrown when an async request times out.
	 */
	@ExceptionHandler(AsyncRequestTimeoutException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleAsyncRequestTimeoutException(final AsyncRequestTimeoutException exception) {
		return createProblem(SERVICE_UNAVAILABLE, "Async request timed out");
	}

	/**
	 * Handle AccessDeniedException. Thrown when access to a resource is denied.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleAccessDeniedException(final AccessDeniedException exception) {
		return createProblem(FORBIDDEN, exception.getMessage());
	}

	/**
	 * Handle AuthenticationException. Thrown when authentication fails.
	 */
	@ExceptionHandler(AuthenticationException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleAuthenticationException(final AuthenticationException exception) {
		return createProblem(UNAUTHORIZED, exception.getMessage());
	}

	/**
	 * Handle UnsupportedOperationException. Thrown when an operation is not supported.
	 */
	@ExceptionHandler(UnsupportedOperationException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleUnsupportedOperationException(final UnsupportedOperationException exception) {
		return createProblem(NOT_IMPLEMENTED, exception.getMessage());
	}

	private ResponseEntity<Problem> createBadRequestProblem(final String detail) {
		return createProblem(BAD_REQUEST, detail);
	}

	private ResponseEntity<Problem> createProblem(final HttpStatus httpStatus, final String detail) {
		final var problem = new ProblemResponse();
		problem.setStatus(Status.valueOf(httpStatus.value()));
		problem.setTitle(httpStatus.getReasonPhrase());
		problem.setDetail(detail);

		return ResponseEntity
			.status(httpStatus)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	private Violation toViolation(final ConstraintViolation<?> constraintViolation) {
		return new Violation(
			constraintViolation.getPropertyPath().toString(),
			constraintViolation.getMessage());
	}

	private Violation toViolation(final FieldError fieldError) {
		return new Violation(
			fieldError.getField(),
			fieldError.getDefaultMessage());
	}
}
