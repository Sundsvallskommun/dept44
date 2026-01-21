package se.sundsvall.dept44.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ThrowableProblemTest {

	@Test
	void constructorWithAllFields() {
		final var type = URI.create("https://example.com/problem");
		final var instance = URI.create("https://example.com/instance/123");

		final var problem = new ThrowableProblem(type, "Title", Status.BAD_REQUEST, "Detail", instance);

		assertThat(problem.getType()).isEqualTo(type);
		assertThat(problem.getTitle()).isEqualTo("Title");
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getDetail()).isEqualTo("Detail");
		assertThat(problem.getInstance()).isEqualTo(instance);
	}

	@Test
	void constructorWithCause() {
		final var cause = new ThrowableProblem(null, "Cause", Status.INTERNAL_SERVER_ERROR, "Cause detail", null);
		final var problem = new ThrowableProblem(null, "Title", Status.BAD_GATEWAY, "Detail", null, cause);

		assertThat(problem.getCause()).isEqualTo(cause);
		assertThat(problem.getCauseAsProblem()).isEqualTo(cause);
	}

	@Test
	void constructorWithNullType() {
		final var problem = new ThrowableProblem(null, "Title", Status.BAD_REQUEST, "Detail", null);

		assertThat(problem.getType()).isEqualTo(Problem.DEFAULT_TYPE);
	}

	@Test
	void constructorWithNullStatus() {
		final var problem = new ThrowableProblem(null, "Title", null, "Detail", null);

		assertThat(problem.getStatus()).isNull();
	}

	@Test
	void getMessageWithTitleAndDetail() {
		final var problem = new ThrowableProblem(null, "Title", Status.BAD_REQUEST, "Detail", null);

		assertThat(problem.getMessage()).isEqualTo("Title: Detail");
	}

	@Test
	void getMessageWithDetailOnly() {
		final var problem = new ThrowableProblem(null, null, Status.BAD_REQUEST, "Detail", null);

		assertThat(problem.getMessage()).isEqualTo("Detail");
	}

	@Test
	void getMessageWithTitleOnly() {
		final var problem = new ThrowableProblem(null, "Title", Status.BAD_REQUEST, null, null);

		assertThat(problem.getMessage()).isEqualTo("Title");
	}

	@Test
	void getMessageWithStatusOnly() {
		final var problem = new ThrowableProblem(null, null, Status.BAD_REQUEST, null, null);

		assertThat(problem.getMessage()).isEqualTo("Bad Request");
	}

	@Test
	void getMessageWithNothing() {
		final var problem = new ThrowableProblem(null, null, null, null, null);

		assertThat(problem.getMessage()).isEqualTo("Unknown problem");
	}

	@Test
	void getCauseAsProblemWithNonProblemCause() {
		final var problem = Problem.valueOf(Status.BAD_REQUEST, "Test");

		assertThat(problem.getCauseAsProblem()).isNull();
	}

	@Test
	void getCauseAsProblemWithNoCause() {
		final var problem = new ThrowableProblem(null, "Title", Status.BAD_REQUEST, "Detail", null);

		assertThat(problem.getCauseAsProblem()).isNull();
	}
}
