package se.sundsvall.dept44.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.zalando.problem.Status.BAD_REQUEST;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

class ClientProblemTest {

	@Test
	void clientProblem() throws URISyntaxException {

		final var problem = new ClientProblem(Status.valueOf(BAD_REQUEST.getStatusCode()), "Error detail");

		assertThat(problem)
			.hasMessage("Bad Request: Error detail")
			.isInstanceOf(AbstractThrowableProblem.class)
			.extracting(ClientProblem::getType, ClientProblem::getTitle, ClientProblem::getStatus, ClientProblem::getDetail)
			.containsExactly(new URI("about:blank"), "Bad Request", BAD_REQUEST, "Error detail");
	}
}
