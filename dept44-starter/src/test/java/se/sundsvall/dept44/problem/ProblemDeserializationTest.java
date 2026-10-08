package se.sundsvall.dept44.problem;

import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblemResponse;
import se.sundsvall.dept44.problem.violations.Violation;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The problem types are part of every service's API, so clients and tests read them back from JSON.
 */
class ProblemDeserializationTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void throwableProblem() {
		final var problem = jsonMapper.readValue("{\"title\":\"Not Found\",\"status\":404,\"detail\":\"missing\"}", ThrowableProblem.class);

		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("missing");
	}

	@Test
	void problem() {
		final var problem = jsonMapper.readValue("{\"title\":\"Not Found\",\"status\":404,\"detail\":\"missing\"}", Problem.class);

		assertThat(problem).isInstanceOf(ProblemResponse.class);
		assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
	}

	@Test
	void constraintViolationProblemResponse() {
		final var json = "{\"title\":\"Constraint Violation\",\"status\":400,\"violations\":[{\"field\":\"name\",\"message\":\"must not be blank\"}]}";

		final var problem = jsonMapper.readValue(json, ConstraintViolationProblemResponse.class);

		assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(problem.getTitle()).isEqualTo("Constraint Violation");
		assertThat(problem.getViolations()).containsExactly(new Violation("name", "must not be blank"));
	}

	@Test
	void statusWithoutAnHttpStatusConstant() {
		final var json = "{\"title\":\"Client Closed Request\",\"status\":499,\"detail\":\"gone\"}";

		assertThat(jsonMapper.readValue(json, ProblemResponse.class).getDetail()).isEqualTo("gone");
		assertThat(jsonMapper.readValue(json, ThrowableProblem.class).getDetail()).isEqualTo("gone");
	}

	@Test
	void throwableProblemSerializesWithoutCauseAsProblem() {
		final var json = jsonMapper.writeValueAsString(Problem.valueOf(NOT_FOUND, "missing"));

		assertThat(json).doesNotContain("causeAsProblem");
	}
}
