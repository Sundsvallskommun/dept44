package se.sundsvall.dept44.problem;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblemResponse;
import se.sundsvall.dept44.problem.violations.Violation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class ProblemResponseTest {

	@Test
	void defaultConstructor() {
		final var response = new ProblemResponse();

		assertThat(response.getType()).isEqualTo(Problem.DEFAULT_TYPE);
		assertThat(response.getTypeForJson()).isNull();
		assertThat(response.getTitle()).isNull();
		assertThat(response.getStatus()).isNull();
		assertThat(response.getStatusCode()).isNull();
		assertThat(response.getDetail()).isNull();
		assertThat(response.getInstance()).isNull();
	}

	@Test
	void constructorFromProblemWithNullType() {
		final var problem = Problem.valueOf(NOT_FOUND, "Not found");

		final var response = new ProblemResponse(problem);

		assertThat(response.getType()).isEqualTo(Problem.DEFAULT_TYPE);
		assertThat(response.getTypeForJson()).isNull();
		assertThat(response.getTitle()).isEqualTo("Not Found");
		assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(response.getStatusCode()).isEqualTo(404);
		assertThat(response.getDetail()).isEqualTo("Not found");
	}

	@Test
	void constructorFromProblemWithDefaultType() {
		final var problem = new ThrowableProblem(Problem.DEFAULT_TYPE, "Bad Request", BAD_REQUEST, "Invalid input", null);

		final var response = new ProblemResponse(problem);

		assertThat(response.getType()).isEqualTo(Problem.DEFAULT_TYPE);
		assertThat(response.getTypeForJson()).isNull();
	}

	@Test
	void constructorFromProblemWithCustomType() {
		final var customType = URI.create("https://example.com/problem/custom");
		final var problem = new ThrowableProblem(customType, "Custom Error", INTERNAL_SERVER_ERROR, "Something went wrong", null);

		final var response = new ProblemResponse(problem);

		assertThat(response.getType()).isEqualTo(customType);
		assertThat(response.getTypeForJson()).isEqualTo(customType);
		assertThat(response.getTitle()).isEqualTo("Custom Error");
		assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
		assertThat(response.getStatusCode()).isEqualTo(500);
		assertThat(response.getDetail()).isEqualTo("Something went wrong");
	}

	@Test
	void constructorFromProblemWithInstance() {
		final var instance = URI.create("/api/resource/123");
		final var problem = new ThrowableProblem(null, "Error", BAD_REQUEST, "Detail", instance);

		final var response = new ProblemResponse(problem);

		assertThat(response.getInstance()).isEqualTo(instance);
	}

	@Test
	void fromThrowableProblem() {
		final var problem = Problem.valueOf(NOT_FOUND, "Resource not found");

		final var response = ProblemResponse.from(problem);

		assertThat(response).isInstanceOf(ProblemResponse.class).isNotInstanceOf(ConstraintViolationProblemResponse.class);
		assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(response.getDetail()).isEqualTo("Resource not found");
	}

	@Test
	void fromConstraintViolationProblem() {
		final var violations = List.of(
			new Violation("field1", "must not be null"),
			new Violation("field2", "must be positive"));
		final var problem = ConstraintViolationProblem.builder()
			.withStatus(BAD_REQUEST)
			.withViolations(violations)
			.build();

		final var response = ProblemResponse.from(problem);

		assertThat(response).isInstanceOf(ConstraintViolationProblemResponse.class);
		final var cvResponse = (ConstraintViolationProblemResponse) response;
		assertThat(cvResponse.getViolations()).hasSize(2);
		assertThat(cvResponse.getStatus()).isEqualTo(BAD_REQUEST);
	}

	@Test
	void settersAndGetters() {
		final var response = new ProblemResponse();
		final var customType = URI.create("https://example.com/error");
		final var instance = URI.create("/api/test");

		response.setType(customType);
		response.setTitle("Test Title");
		response.setStatus(FORBIDDEN);
		response.setDetail("Test detail");
		response.setInstance(instance);

		assertThat(response.getType()).isEqualTo(customType);
		assertThat(response.getTypeForJson()).isEqualTo(customType);
		assertThat(response.getTitle()).isEqualTo("Test Title");
		assertThat(response.getStatus()).isEqualTo(FORBIDDEN);
		assertThat(response.getStatusCode()).isEqualTo(403);
		assertThat(response.getDetail()).isEqualTo("Test detail");
		assertThat(response.getInstance()).isEqualTo(instance);
	}

	@Test
	void getTypeReturnsDefaultTypeWhenNull() {
		final var response = new ProblemResponse();

		assertThat(response.getType()).isEqualTo(Problem.DEFAULT_TYPE);
	}

	@Test
	void getTypeReturnsSetTypeWhenNotNull() {
		final var response = new ProblemResponse();
		final var customType = URI.create("https://custom.type");
		response.setType(customType);

		assertThat(response.getType()).isEqualTo(customType);
	}

	@Test
	void getStatusCodeReturnsNullWhenStatusIsNull() {
		final var response = new ProblemResponse();

		assertThat(response.getStatusCode()).isNull();
	}

	@Test
	void getStatusCodeReturnsCodeWhenStatusIsSet() {
		final var response = new ProblemResponse();
		response.setStatus(CONFLICT);

		assertThat(response.getStatusCode()).isEqualTo(409);
	}
}
