package se.sundsvall.dept44.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
		when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError1, fieldError2));

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
		final var problem = new ThrowableProblem(null, "Error", null, "Something went wrong", null);

		// Act
		final var response = handler.handleThrowableProblem(problem);

		// Assert
		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void handleConstraintViolationProblem() {
		// Arrange
		final var violations = java.util.List.of(
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
