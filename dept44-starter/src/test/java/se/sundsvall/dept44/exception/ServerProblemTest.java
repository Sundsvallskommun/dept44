package se.sundsvall.dept44.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.dept44.problem.Status.BAD_GATEWAY;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.problem.AbstractThrowableProblem;
import se.sundsvall.dept44.problem.Status;

class ServerProblemTest {

	@Test
	void serverProblem() throws URISyntaxException {

		final var problem = new ServerProblem(Status.valueOf(BAD_GATEWAY.getStatusCode()), "Error detail");

		assertThat(problem)
			.hasMessage("Bad Gateway: Error detail")
			.isInstanceOf(AbstractThrowableProblem.class)
			.extracting(ServerProblem::getType, ServerProblem::getTitle, ServerProblem::getStatus, ServerProblem::getDetail)
			.containsExactly(new URI("about:blank"), "Bad Gateway", BAD_GATEWAY, "Error detail");
	}
}
