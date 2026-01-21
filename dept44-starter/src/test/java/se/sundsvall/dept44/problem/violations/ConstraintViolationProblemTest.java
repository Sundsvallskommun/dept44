package se.sundsvall.dept44.problem.violations;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.problem.Status;

class ConstraintViolationProblemTest {

	@Test
	void defaultConstants() {
		assertThat(URI.create("about:blank")).isEqualTo(ConstraintViolationProblem.TYPE);
		assertThat(ConstraintViolationProblem.DEFAULT_TITLE).isEqualTo("Constraint Violation");
	}

	@Test
	void builderWithAllFields() {
		final var type = URI.create("https://example.com/constraint-violation");
		final var violations = List.of(
			new Violation("field1", "error1"),
			new Violation("field2", "error2"));

		final var problem = ConstraintViolationProblem.builder()
			.withType(type)
			.withStatus(Status.BAD_REQUEST)
			.withTitle("Custom Title")
			.withViolations(violations)
			.build();

		assertThat(problem.getType()).isEqualTo(type);
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Custom Title");
		assertThat(problem.getViolations()).hasSize(2);
	}

	@Test
	void builderWithDefaults() {
		final var problem = ConstraintViolationProblem.builder()
			.withStatus(Status.BAD_REQUEST)
			.build();

		assertThat(problem.getType()).isEqualTo(ConstraintViolationProblem.TYPE);
		assertThat(problem.getTitle()).isEqualTo(ConstraintViolationProblem.DEFAULT_TITLE);
		assertThat(problem.getViolations()).isEmpty();
	}

	@Test
	void constructorWithStatusAndViolations() {
		final var violations = List.of(new Violation("field", "error"));

		final var problem = new ConstraintViolationProblem(Status.BAD_REQUEST, violations);

		assertThat(problem.getType()).isEqualTo(ConstraintViolationProblem.TYPE);
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo(ConstraintViolationProblem.DEFAULT_TITLE);
		assertThat(problem.getViolations()).hasSize(1);
	}

	@Test
	void jsonCreatorConstructor() {
		final var type = URI.create("https://example.com/problem");
		final var violations = List.of(new Violation("field", "error"));

		final var problem = new ConstraintViolationProblem(type, 400, violations);

		assertThat(problem.getType()).isEqualTo(type);
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getViolations()).hasSize(1);
	}

	@Test
	void jsonCreatorConstructorWithNullStatus() {
		final var problem = new ConstraintViolationProblem(null, null, null);

		assertThat(problem.getType()).isEqualTo(ConstraintViolationProblem.TYPE);
		assertThat(problem.getStatus()).isNull();
		assertThat(problem.getViolations()).isEmpty();
	}

	@Test
	void constructorWithNullViolations() {
		final var problem = new ConstraintViolationProblem(Status.BAD_REQUEST, null);

		assertThat(problem.getViolations()).isEmpty();
	}

	@Test
	void violationsAreImmutable() {
		final var violations = List.of(new Violation("field", "error"));
		final var problem = new ConstraintViolationProblem(Status.BAD_REQUEST, violations);

		final var returnedViolations = problem.getViolations();

		assertThat(returnedViolations).isUnmodifiable();
	}

	@Test
	void equalsAndHashCode() {
		final var violations = List.of(new Violation("field", "error"));

		final var problem1 = new ConstraintViolationProblem(Status.BAD_REQUEST, violations);
		final var problem2 = new ConstraintViolationProblem(Status.BAD_REQUEST, violations);
		final var problem3 = new ConstraintViolationProblem(Status.NOT_FOUND, violations);
		final var problem4 = new ConstraintViolationProblem(Status.BAD_REQUEST, List.of());

		assertThat(problem1)
			.isEqualTo(problem2).hasSameHashCodeAs(problem2)
			.isNotEqualTo(problem3)
			.isNotEqualTo(problem4)
			.isNotEqualTo(null)
			.isNotEqualTo(new Object());
	}

	@Test
	@SuppressWarnings("EqualsWithItself")
	void equalsSameInstance() {
		final var problem = new ConstraintViolationProblem(Status.BAD_REQUEST, List.of());

		assertThat(problem.equals(problem)).isTrue();
	}

	@Test
	void constructorWithNullType() {
		final var problem = new ConstraintViolationProblem(null, Status.BAD_REQUEST, List.of(), null);

		assertThat(problem.getType()).isEqualTo(ConstraintViolationProblem.TYPE);
	}

	@Test
	void constructorWithNullTitle() {
		final var problem = new ConstraintViolationProblem(null, Status.BAD_REQUEST, List.of(), null);

		assertThat(problem.getTitle()).isEqualTo(ConstraintViolationProblem.DEFAULT_TITLE);
	}

	@Test
	void constructorWithCustomTitle() {
		final var problem = new ConstraintViolationProblem(null, Status.BAD_REQUEST, List.of(), "Custom Title");

		assertThat(problem.getTitle()).isEqualTo("Custom Title");
	}
}
