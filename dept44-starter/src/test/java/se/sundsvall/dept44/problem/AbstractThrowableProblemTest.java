package se.sundsvall.dept44.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;

class AbstractThrowableProblemTest {

	@Test
	void constructorWithAllFields() {
		final var type = URI.create("https://example.com/problem");
		final var instance = URI.create("https://example.com/instance/123");
		final var cause = new ClientProblem(Status.BAD_REQUEST, "Cause");

		final var problem = new TestProblem(type, "Title", Status.BAD_REQUEST, "Detail", instance, cause);

		assertThat(problem.getType()).isEqualTo(type);
		assertThat(problem.getTitle()).isEqualTo("Title");
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getDetail()).isEqualTo("Detail");
		assertThat(problem.getInstance()).isEqualTo(instance);
		assertThat(problem.getCause()).isEqualTo(cause);
	}

	@Test
	void constructorWithoutCause() {
		final var type = URI.create("https://example.com/problem");
		final var instance = URI.create("https://example.com/instance/123");

		final var problem = new TestProblem(type, "Title", Status.BAD_REQUEST, "Detail", instance);

		assertThat(problem.getType()).isEqualTo(type);
		assertThat(problem.getInstance()).isEqualTo(instance);
		assertThat(problem.getCause()).isNull();
	}

	@Test
	void constructorWithoutInstance() {
		final var type = URI.create("https://example.com/problem");

		final var problem = new TestProblem(type, "Title", Status.BAD_REQUEST, "Detail");

		assertThat(problem.getType()).isEqualTo(type);
		assertThat(problem.getInstance()).isNull();
	}

	@Test
	void constructorMinimal() {
		final var type = URI.create("https://example.com/problem");

		final var problem = new TestProblem(type, "Title", Status.BAD_REQUEST);

		assertThat(problem.getType()).isEqualTo(type);
		assertThat(problem.getTitle()).isEqualTo("Title");
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getDetail()).isNull();
	}

	@Test
	void clientProblemUsesCorrectDefaults() {
		final var problem = new ClientProblem(Status.BAD_REQUEST, "Bad input");

		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Bad input");
	}

	@Test
	void serverProblemUsesCorrectDefaults() {
		final var problem = new ServerProblem(Status.INTERNAL_SERVER_ERROR, "Server error");

		assertThat(problem.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
		assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
		assertThat(problem.getDetail()).isEqualTo("Server error");
	}

	/**
	 * Concrete implementation for testing the abstract class.
	 */
	private static class TestProblem extends AbstractThrowableProblem {

		TestProblem(final URI type, final String title, final StatusType status, final String detail, final URI instance, final ThrowableProblem cause) {
			super(type, title, status, detail, instance, cause);
		}

		TestProblem(final URI type, final String title, final StatusType status, final String detail, final URI instance) {
			super(type, title, status, detail, instance);
		}

		TestProblem(final URI type, final String title, final StatusType status, final String detail) {
			super(type, title, status, detail);
		}

		TestProblem(final URI type, final String title, final StatusType status) {
			super(type, title, status);
		}
	}
}
