package se.sundsvall.dept44.rabbitmq.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class RequestIdHeadersTest {

	private final RequestIdHeaders requestIdHeaders = new RequestIdHeaders();

	@AfterEach
	void tearDown() {
		RequestId.reset();
		Identifier.remove();
	}

	@Test
	void sendsTheRequestIdAndIdentifierAlong() {
		RequestId.init("request-id");
		Identifier.set(Identifier.parse("joe01doe; type=adAccount"));

		final var message = requestIdHeaders.postProcessMessage(new Message(new byte[0]));

		assertThat(message.getMessageProperties().getHeaders()).containsOnly(
			entry("x-request-id", "request-id"),
			entry("X-Sent-By", "joe01doe; type=adAccount"));
	}

	@Test
	void sendsNothingOutsideARequest() {
		final var message = requestIdHeaders.postProcessMessage(new Message(new byte[0]));

		assertThat(message.getMessageProperties().getHeaders()).isEmpty();
	}

	@Test
	void keepsHeadersTheServiceSet() {
		RequestId.init("request-id");
		Identifier.set(Identifier.parse("joe01doe; type=adAccount"));
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("x-request-id", "own-request-id");
		message.getMessageProperties().setHeader("X-Sent-By", "own; type=partyId");

		requestIdHeaders.postProcessMessage(message);

		assertThat(message.getMessageProperties().getHeaders()).containsOnly(
			entry("x-request-id", "own-request-id"),
			entry("X-Sent-By", "own; type=partyId"));
	}
}
