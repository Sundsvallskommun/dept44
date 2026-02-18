package se.sundsvall.dept44.exception;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

class ServerProblemTest {

	@Test
	void serverProblem() throws URISyntaxException {

		final var problem = new ServerProblem(BAD_GATEWAY, "Error detail");

		assertThat(problem)
			.hasMessage("Bad Gateway: Error detail")
			.isInstanceOf(ThrowableProblem.class)
			.extracting(ServerProblem::getType, ServerProblem::getTitle, ServerProblem::getStatus, ServerProblem::getDetail)
			.containsExactly(new URI("about:blank"), "Bad Gateway", BAD_GATEWAY, "Error detail");
	}
}
