package se.sundsvall.dept44.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ProblemTest {

	@Test
	void defaultType() {
		assertThat(URI.create("about:blank")).isEqualTo(Problem.DEFAULT_TYPE);
	}

	@Test
	void builder() {
		final var problem = Problem.builder()
			.withType(URI.create("https://example.com/problem"))
			.withTitle("Test Title")
			.withStatus(Status.BAD_REQUEST)
			.withDetail("Test detail")
			.withInstance(URI.create("https://example.com/instance/123"))
			.build();

		assertThat(problem.getType()).isEqualTo(URI.create("https://example.com/problem"));
		assertThat(problem.getTitle()).isEqualTo("Test Title");
		assertThat(problem.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(problem.getDetail()).isEqualTo("Test detail");
		assertThat(problem.getInstance()).isEqualTo(URI.create("https://example.com/instance/123"));
	}

	@Test
	void builderWithDefaults() {
		final var problem = Problem.builder()
			.withStatus(Status.NOT_FOUND)
			.build();

		assertThat(problem.getType()).isEqualTo(Problem.DEFAULT_TYPE);
		assertThat(problem.getTitle()).isNull();
		assertThat(problem.getStatus()).isEqualTo(Status.NOT_FOUND);
		assertThat(problem.getDetail()).isNull();
		assertThat(problem.getInstance()).isNull();
	}

	@Test
	void valueOf() {
		final var problem = Problem.valueOf(Status.NOT_FOUND, "Resource not found");

		assertThat(problem.getStatus()).isEqualTo(Status.NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("Resource not found");
	}

	@Test
	void valueOfWithDifferentStatuses() {
		final var badRequest = Problem.valueOf(Status.BAD_REQUEST, "Invalid input");
		assertThat(badRequest.getStatus()).isEqualTo(Status.BAD_REQUEST);
		assertThat(badRequest.getTitle()).isEqualTo("Bad Request");

		final var serverError = Problem.valueOf(Status.INTERNAL_SERVER_ERROR, "Server error");
		assertThat(serverError.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
		assertThat(serverError.getTitle()).isEqualTo("Internal Server Error");
	}

	@Test
	void valueOfWithStatusOnly() {
		final var problem = Problem.valueOf(Status.FORBIDDEN);

		assertThat(problem.getStatus()).isEqualTo(Status.FORBIDDEN);
		assertThat(problem.getTitle()).isEqualTo("Forbidden");
		assertThat(problem.getDetail()).isNull();
	}

	@Test
	void builderWithCause() {
		final var cause = Problem.valueOf(Status.BAD_REQUEST, "Original error");
		final var problem = Problem.builder()
			.withStatus(Status.BAD_GATEWAY)
			.withTitle("Gateway Error")
			.withDetail("Upstream error")
			.withCause(cause)
			.build();

		assertThat(problem.getStatus()).isEqualTo(Status.BAD_GATEWAY);
		assertThat(problem.getCause()).isEqualTo(cause);
	}
}
