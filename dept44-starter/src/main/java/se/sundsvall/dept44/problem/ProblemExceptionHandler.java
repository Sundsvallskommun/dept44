package se.sundsvall.dept44.problem;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

/**
 * Global exception handler for validation exceptions. Converts validation exceptions to RFC 7807 Problem responses.
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
