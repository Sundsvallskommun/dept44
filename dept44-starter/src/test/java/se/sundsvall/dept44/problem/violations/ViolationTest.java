package se.sundsvall.dept44.problem.violations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ViolationTest {

	@Test
	void constructor() {
		final var violation = new Violation("fieldName", "error message");

		assertThat(violation.field()).isEqualTo("fieldName");
		assertThat(violation.message()).isEqualTo("error message");
	}

	@Test
	void constructorWithNullValues() {
		final var violation = new Violation(null, null);

		assertThat(violation.field()).isNull();
		assertThat(violation.message()).isNull();
	}

	@Test
	void equalsAndHashCode() {
		final var violation1 = new Violation("field", "message");
		final var violation2 = new Violation("field", "message");
		final var violation3 = new Violation("otherField", "message");
		final var violation4 = new Violation("field", "otherMessage");

		assertThat(violation1)
			.isEqualTo(violation2)
			.hasSameHashCodeAs(violation2)
			.isNotEqualTo(violation3)
			.isNotEqualTo(violation4)
			.isNotEqualTo(null)
			.isNotEqualTo(new Object());
	}

	@Test
	@SuppressWarnings("EqualsWithItself")
	void equalsSameInstance() {
		final var violation = new Violation("field", "message");

		assertThat(violation.equals(violation)).isTrue();
	}

	@Test
	void toStringContainsFieldAndMessage() {
		final var violation = new Violation("email", "must be valid");

		final var result = violation.toString();

		assertThat(result).contains("email").contains("must be valid");
	}
}
