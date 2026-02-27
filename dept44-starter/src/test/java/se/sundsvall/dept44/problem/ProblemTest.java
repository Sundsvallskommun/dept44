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
	void badRequest() {
		final var problem = Problem.badRequest();

		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isNull();
	}

	@Test
	void badRequestWithDetail() {
		final var problem = Problem.badRequest("Something was wrong with the input");

		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Something was wrong with the input");
	}

	@Test
	void badRequestWithDetailAndParameters() {
		final var problem = Problem.badRequest("Something cannot be more than {0} characters long", 12);

		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Something cannot be more than 12 characters long");
	}

	@Test
	void notFound() {
		final var problem = Problem.notFound();

		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isNull();
	}

	@Test
	void notFoundWithDetail() {
		final var problem = Problem.notFound("Something was not found");

		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("Something was not found");
	}

	@Test
	void notFoundWithDetailAndParameters() {
		final var problem = Problem.notFound("Something was not found with id {0}", 567);

		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("Something was not found with id 567");
	}

	@Test
	void internalServerError() {
		final var problem = Problem.internalServerError();

		assertThat(problem.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
		assertThat(problem.getDetail()).isNull();
	}

	@Test
	void internalServerErrorWithDetail() {
		final var problem = Problem.internalServerError("Something went wrong");

		assertThat(problem.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
		assertThat(problem.getDetail()).isEqualTo("Something went wrong");
	}

	@Test
	void internalServerErrorWithDetailAndParameters() {
		final var problem = Problem.internalServerError("Something went wrong. Try again in {0} minutes", 5);

		assertThat(problem.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
		assertThat(problem.getDetail()).isEqualTo("Something went wrong. Try again in 5 minutes");
	}

	@Test
	void badGateway() {
		final var problem = Problem.badGateway();

		assertThat(problem.getStatus()).isEqualTo(BAD_GATEWAY);
		assertThat(problem.getTitle()).isEqualTo("Bad Gateway");
		assertThat(problem.getDetail()).isNull();
	}

	@Test
	void badGatewayWithDetail() {
		final var problem = Problem.badGateway("No response from some-service");

		assertThat(problem.getStatus()).isEqualTo(BAD_GATEWAY);
		assertThat(problem.getTitle()).isEqualTo("Bad Gateway");
		assertThat(problem.getDetail()).isEqualTo("No response from some-service");
	}

	@Test
	void badGatewayWithDetailAndParameters() {
		final var problem = Problem.badGateway("No response from some-service after {0} attempts", 3);

		assertThat(problem.getStatus()).isEqualTo(BAD_GATEWAY);
		assertThat(problem.getTitle()).isEqualTo("Bad Gateway");
		assertThat(problem.getDetail()).isEqualTo("No response from some-service after 3 attempts");
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
