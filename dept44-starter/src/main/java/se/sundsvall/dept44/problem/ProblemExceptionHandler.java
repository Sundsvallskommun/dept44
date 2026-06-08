package se.sundsvall.dept44.problem;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

/**
 * Global exception handler for validation exceptions. Converts validation exceptions to RFC 9457 Problem responses.
 */
@ControllerAdvice
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProblemExceptionHandler.class);

	private static final String CONSTRAINT_VIOLATION_TITLE = "Constraint Violation";

	private static final String MDC_REQUEST_PATH = "requestPath";
	private static final String MDC_HTTP_METHOD = "httpMethod";
	private static final String MDC_PROBLEM_TITLE = "problemTitle";
	private static final String MDC_INTEGRATION_NAME = "integrationName";

	/**
	 * Override the central exception handling method to convert ProblemDetail responses to our ProblemResponse format. This
	 * ensures all exceptions handled by ResponseEntityExceptionHandler produce the same JSON format as before.
	 */
	@Override
	protected @Nullable ResponseEntity<@NonNull Object> handleExceptionInternal(
		final @NonNull Exception ex, @Nullable final Object body, final @NonNull HttpHeaders headers, final @NonNull HttpStatusCode statusCode, final @NonNull WebRequest request) {

		var convertedBody = body;
		var resolvedStatus = statusCode;
		if (ex instanceof final ThrowableProblem throwableProblem && convertedBody == null) {
			convertedBody = ProblemResponse.from(throwableProblem);
			final var problemStatus = throwableProblem.getStatus();
			if (problemStatus != null) {
				resolvedStatus = problemStatus;
			}
		} else if (convertedBody == null && ex instanceof final org.springframework.web.ErrorResponse errorResponse) {
			convertedBody = toProblemResponse(errorResponse.getBody());
		} else if (convertedBody instanceof final ProblemDetail pd) {
			convertedBody = toProblemResponse(pd);
		}
		var resolvedHeaders = headers;
		if (resolvedHeaders.getContentType() == null) {
			resolvedHeaders = new HttpHeaders(headers);
			resolvedHeaders.setContentType(APPLICATION_PROBLEM_JSON);
		}
		return super.handleExceptionInternal(ex, convertedBody, resolvedHeaders, resolvedStatus, request);
	}

	/**
	 * Override to produce ConstraintViolationProblem with violations array from MethodArgumentNotValidException.
	 */
	@Override
	protected @Nullable ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(
		final @NonNull MethodArgumentNotValidException ex, final @NonNull HttpHeaders headers, final @NonNull HttpStatusCode status, final @NonNull WebRequest request) {

		final var problem = toConstraintViolationProblem(ex);
		return handleExceptionInternal(ex, ProblemResponse.from(problem), headers, status, request);
	}

	/**
	 * Handle Jakarta validation ConstraintViolationException. Typically, thrown when @Validated is used on path/query
	 * parameters.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	@ResponseBody
	public ResponseEntity<ProblemResponse> handleConstraintViolationException(final ConstraintViolationException exception) {
		final var violations = exception.getConstraintViolations().stream()
			.map(this::toViolation)
			.toList();

		final var problem = ConstraintViolationProblem.builder()
			.withStatus(BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(violations)
			.build();

		return ResponseEntity
			.status(BAD_REQUEST)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(ProblemResponse.from(problem));
	}

	/**
	 * Handle BindException. Thrown when binding errors occur during form submission.
	 */
	@ExceptionHandler(BindException.class)
	@ResponseBody
	public ResponseEntity<ProblemResponse> handleBindException(final BindException exception) {
		final var allViolations = getViolations(exception);

		final var problem = ConstraintViolationProblem.builder()
			.withStatus(BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(allViolations)
			.build();

		return ResponseEntity
			.status(BAD_REQUEST)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(ProblemResponse.from(problem));
	}

	/**
	 * Extract violations from both field errors and global errors in BindException and combine them into a single list of
	 * Violation objects.
	 *
	 * @param  exception the BindException containing field and global errors
	 * @return           a combined list of Violation objects representing all validation errors
	 */
	private List<Violation> getViolations(final BindException exception) {
		final var fieldViolations = exception.getBindingResult().getFieldErrors().stream()
			.map(this::toViolation);

		final var globalViolations = exception.getBindingResult().getGlobalErrors().stream()
			.map(this::toViolation);

		return Stream.concat(fieldViolations, globalViolations).toList();
	}

	/**
	 * Handle Resilience4j CallNotPermittedException. Thrown when a circuit breaker is open and rejects the call.
	 */
	@ExceptionHandler(CallNotPermittedException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleCallNotPermittedException(final CallNotPermittedException exception, final HttpServletRequest request) {
		logWithContext(request, SERVICE_UNAVAILABLE.getReasonPhrase(), exception.getCausingCircuitBreakerName(),
			() -> LOGGER.error("Circuit breaker '{}' is open, responding with {}", exception.getCausingCircuitBreakerName(), SERVICE_UNAVAILABLE.value()));

		final var problem = Problem.valueOf(SERVICE_UNAVAILABLE, exception.getMessage());

		return ResponseEntity
			.status(SERVICE_UNAVAILABLE)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(ProblemResponse.from(problem));
	}

	/**
	 * Handle AccessDeniedException. Thrown when access to a resource is denied.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleAccessDeniedException(final AccessDeniedException exception, final HttpServletRequest request) {
		logWithContext(request, FORBIDDEN.getReasonPhrase(), null,
			() -> LOGGER.warn("Access denied ({}), responding with {}", exception.getClass().getSimpleName(), FORBIDDEN.value()));

		return createProblem(FORBIDDEN, exception.getMessage());
	}

	/**
	 * Handle AuthenticationException. Thrown when authentication fails.
	 */
	@ExceptionHandler(AuthenticationException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleAuthenticationException(final AuthenticationException exception, final HttpServletRequest request) {
		logWithContext(request, UNAUTHORIZED.getReasonPhrase(), null,
			() -> LOGGER.warn("Authentication failed ({}), responding with {}", exception.getClass().getSimpleName(), UNAUTHORIZED.value()));

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

	/**
	 * Handle MultipartException. Thrown when multipart request parsing fails (e.g. missing parts, size exceeded).
	 */
	@ExceptionHandler(MultipartException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleMultipartException(final MultipartException exception) {
		return createProblem(BAD_REQUEST, exception.getMessage());
	}

	/**
	 * Handle SocketTimeoutException. Thrown when a downstream service call times out.
	 */
	@ExceptionHandler(SocketTimeoutException.class)
	@ResponseBody
	public ResponseEntity<Problem> handleSocketTimeoutException(final SocketTimeoutException exception, final HttpServletRequest request) {
		logWithContext(request, GATEWAY_TIMEOUT.getReasonPhrase(), null,
			() -> LOGGER.error("Downstream call timed out, responding with {}: {}", GATEWAY_TIMEOUT.value(), exception.getMessage()));

		return createProblem(GATEWAY_TIMEOUT, exception.getMessage());
	}

	/**
	 * Catch-all handler for any unhandled exception. Ensures all errors produce a Problem JSON response instead of falling
	 * through to the Servlet container's default error handling.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseBody
	public ResponseEntity<Problem> handleException(final Exception exception, final HttpServletRequest request) {
		logWithContext(request, INTERNAL_SERVER_ERROR.getReasonPhrase(), null,
			() -> LOGGER.error("Unhandled exception caught by global handler, responding with {}", INTERNAL_SERVER_ERROR.value(), exception));

		return createProblem(INTERNAL_SERVER_ERROR, exception.getMessage());
	}

	private ProblemResponse toProblemResponse(final ProblemDetail pd) {
		final var response = new ProblemResponse();
		final var type = pd.getType();
		if (type != null && !Problem.DEFAULT_TYPE.equals(type)) {
			response.setType(type);
		}
		response.setTitle(pd.getTitle());
		response.setStatus(HttpStatus.valueOf(pd.getStatus()));
		response.setDetail(pd.getDetail());
		response.setInstance(pd.getInstance());
		return response;
	}

	private ConstraintViolationProblem toConstraintViolationProblem(final BindException exception) {
		final var violations = getViolations(exception);

		return ConstraintViolationProblem.builder()
			.withStatus(BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(violations)
			.build();
	}

	/**
	 * Put the given context on the MDC, run the log statement so the values surface as structured (and OpenSearch
	 * filterable) fields, then remove the keys again. Cleanup is mandatory since servlet threads are pooled and would
	 * otherwise leak these fields onto subsequent requests.
	 *
	 * @param request         the current request, used for path and HTTP method
	 * @param problemTitle    the title of the Problem being returned
	 * @param integrationName the target integration name, or {@code null} when not available
	 * @param logStatement    the logging call to execute while the context is on the MDC
	 */
	private static void logWithContext(final HttpServletRequest request, final String problemTitle, final String integrationName, final Runnable logStatement) {
		try {
			MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
			MDC.put(MDC_HTTP_METHOD, request.getMethod());
			MDC.put(MDC_PROBLEM_TITLE, problemTitle);
			Optional.ofNullable(integrationName).ifPresent(name -> MDC.put(MDC_INTEGRATION_NAME, name));
			logStatement.run();
		} finally {
			MDC.remove(MDC_REQUEST_PATH);
			MDC.remove(MDC_HTTP_METHOD);
			MDC.remove(MDC_PROBLEM_TITLE);
			MDC.remove(MDC_INTEGRATION_NAME);
		}
	}

	private ResponseEntity<Problem> createProblem(final HttpStatus httpStatus, final String detail) {
		final var problem = new ProblemResponse();
		problem.setStatus(httpStatus);
		problem.setTitle(httpStatus.getReasonPhrase());
		problem.setDetail(detail);

		return ResponseEntity
			.status(httpStatus)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	private Violation toViolation(final ObjectError objectError) {
		return new Violation(
			objectError.getObjectName(),
			objectError.getDefaultMessage());
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
