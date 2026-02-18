package se.sundsvall.dept44.problem;

import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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
			.withStatus(BAD_REQUEST)
			.withDetail("Test detail")
			.withInstance(URI.create("https://example.com/instance/123"))
			.build();

		assertThat(problem.getType()).isEqualTo(URI.create("https://example.com/problem"));
		assertThat(problem.getTitle()).isEqualTo("Test Title");
		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getDetail()).isEqualTo("Test detail");
		assertThat(problem.getInstance()).isEqualTo(URI.create("https://example.com/instance/123"));
	}

	@Test
	void builderWithDefaults() {
		final var problem = Problem.builder()
			.withStatus(NOT_FOUND)
			.build();

		assertThat(problem.getType()).isEqualTo(Problem.DEFAULT_TYPE);
		assertThat(problem.getTitle()).isNull();
		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getDetail()).isNull();
		assertThat(problem.getInstance()).isNull();
	}

	@Test
	void valueOf() {
		final var problem = Problem.valueOf(NOT_FOUND, "Resource not found");

		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("Resource not found");
	}

	@Test
	void valueOfWithDifferentStatuses() {
		final var badRequest = Problem.valueOf(BAD_REQUEST, "Invalid input");
		assertThat(badRequest.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(badRequest.getTitle()).isEqualTo("Bad Request");

		final var serverError = Problem.valueOf(INTERNAL_SERVER_ERROR, "Server error");
		assertThat(serverError.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(serverError.getTitle()).isEqualTo("Internal Server Error");
	}

	@Test
	void valueOfWithStatusOnly() {
		final var problem = Problem.valueOf(FORBIDDEN);

		assertThat(problem.getStatus()).isEqualTo(FORBIDDEN);
		assertThat(problem.getTitle()).isEqualTo("Forbidden");
		assertThat(problem.getDetail()).isNull();
	}

	@Test
	void getStatusValue() {
		final var problem = Problem.valueOf(NOT_FOUND, "Not found");

		assertThat(problem.getStatusValue()).isEqualTo(404);
	}

	@Test
	void getStatusValueWhenNull() {
		final var problem = Problem.builder().build();

		assertThat(problem.getStatusValue()).isNull();
	}

	@Test
	void builderWithCause() {
		final var cause = Problem.valueOf(BAD_REQUEST, "Original error");
		final var problem = Problem.builder()
			.withStatus(BAD_GATEWAY)
			.withTitle("Gateway Error")
			.withDetail("Upstream error")
			.withCause(cause)
			.build();

		assertThat(problem.getStatus()).isEqualTo(BAD_GATEWAY);
		assertThat(problem.getCause()).isEqualTo(cause);
	}
}
