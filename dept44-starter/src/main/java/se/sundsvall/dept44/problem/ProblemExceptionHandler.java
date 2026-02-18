package se.sundsvall.dept44.problem;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

/**
 * Global exception handler for validation exceptions. Converts validation exceptions to RFC 9457 Problem responses.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String CONSTRAINT_VIOLATION_TITLE = "Constraint Violation";

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
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

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
			.withStatus(BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(violations)
			.build();

		return ResponseEntity
			.status(BAD_REQUEST)
			.contentType(APPLICATION_PROBLEM_JSON)
			.body(problem);
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
			.withStatus(BAD_REQUEST)
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
		final var violations = exception.getBindingResult().getFieldErrors().stream()
			.map(this::toViolation)
			.toList();

		return ConstraintViolationProblem.builder()
			.withStatus(BAD_REQUEST)
			.withTitle(CONSTRAINT_VIOLATION_TITLE)
			.withViolations(violations)
			.build();
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
