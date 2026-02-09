package se.sundsvall.dept44.configuration.feign.decoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URI;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder.DefaultProblemResponse;
import se.sundsvall.dept44.problem.Problem;

class DefaultProblemResponseTest {

	@Test
	void getTypeWithValue() {
		final var response = new DefaultProblemResponse();
		response.setType("https://example.com/problem");

		assertThat(response.getType()).isEqualTo(URI.create("https://example.com/problem"));
	}

	@Test
	void getTypeWithNull() {
		final var response = new DefaultProblemResponse();

		assertThat(response.getType()).isEqualTo(Problem.DEFAULT_TYPE);
	}

	@Test
	void getTitle() {
		final var response = new DefaultProblemResponse();
		response.setTitle("Test Title");

		assertThat(response.getTitle()).isEqualTo("Test Title");
	}

	@Test
	void getStatusWithValue() {
		final var response = new DefaultProblemResponse();
		response.setStatus(400);

		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
	}

	@Test
	void getStatusWithNull() {
		final var response = new DefaultProblemResponse();

		assertThat(response.getStatus()).isNull();
	}

	@Test
	void getDetail() {
		final var response = new DefaultProblemResponse();
		response.setDetail("Test detail");

		assertThat(response.getDetail()).isEqualTo("Test detail");
	}

	@Test
	void getInstanceWithValue() {
		final var response = new DefaultProblemResponse();
		response.setInstance("https://example.com/instance/123");

		assertThat(response.getInstance()).isEqualTo(URI.create("https://example.com/instance/123"));
	}

	@Test
	void getInstanceWithNull() {
		final var response = new DefaultProblemResponse();

		assertThat(response.getInstance()).isNull();
	}

	@Test
	void allSetters() {
		final var response = new DefaultProblemResponse();
		response.setType("https://example.com/problem");
		response.setTitle("Title");
		response.setStatus(404);
		response.setDetail("Detail");
		response.setInstance("https://example.com/instance");

		assertThat(response.getType()).isEqualTo(URI.create("https://example.com/problem"));
		assertThat(response.getTitle()).isEqualTo("Title");
		assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(response.getDetail()).isEqualTo("Detail");
		assertThat(response.getInstance()).isEqualTo(URI.create("https://example.com/instance"));
	}
}
