package se.sundsvall.dept44.configuration.feign.decoder.util;

import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.problem.ThrowableProblem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class ProblemUtilsTest {

	@Test
	void toProblem() {

		// Arrange
		final var violations = List.of(
			new Violation("field1", "error1"),
			new Violation("field2", "error2"),
			new Violation("field3", "error3"));
		final var constraintViolationProblem = new ConstraintViolationProblem(BAD_REQUEST, violations);

		// Act
		final var problem = ProblemUtils.toProblem(constraintViolationProblem);

		// Assert
		assertThat(problem).isExactlyInstanceOf(ThrowableProblem.class);
		assertThat(problem.getDetail()).isEqualTo("field1: error1, field2: error2, field3: error3");
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Constraint Violation");
		assertThat(problem.getType()).isEqualTo(ConstraintViolationProblem.TYPE);
	}

	@Test
	void toProblemWhenInputIsNull() {

		// Act
		final var problem = ProblemUtils.toProblem(null);

		// Assert
		assertThat(problem).isNull();
	}
}
