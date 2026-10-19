package se.sundsvall.dept44.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

class OutgoingMessageTest {

	@Test
	void holdsWhatItWasGiven() {
		final var payload = new Object();

		final var message = OutgoingMessage.to("exchange", "routing-key")
			.withPayload(payload)
			.withMessageId("message-id")
			.withCorrelationId("correlation-id")
			.withReplyTo("reply-exchange", "reply-key")
			.withHeader("x-first", "one")
			.withHeader("x-second", 2);

		assertThat(message.getExchange()).isEqualTo("exchange");
		assertThat(message.getRoutingKey()).isEqualTo("routing-key");
		assertThat(message.getPayload()).isSameAs(payload);
		assertThat(message.getMessageId()).isEqualTo("message-id");
		assertThat(message.getCorrelationId()).isEqualTo("correlation-id");
		assertThat(message.getReplyTo()).isEqualTo(new Address("reply-exchange", "reply-key"));
		assertThat(message.getReplyTo()).hasToString("reply-exchange/reply-key");
		assertThat(message.getHeaders()).containsExactly(entry("x-first", "one"), entry("x-second", 2));
	}

	@Test
	void startsWithOnlyTheDestination() {
		final var message = OutgoingMessage.to("exchange", "");

		assertThat(message.getRoutingKey()).isEmpty();
		assertThat(message.getPayload()).isNull();
		assertThat(message.getMessageId()).isNull();
		assertThat(message.getCorrelationId()).isNull();
		assertThat(message.getReplyTo()).isNull();
		assertThat(message.getHeaders()).isEmpty();
	}

	@Test
	void requiresExchangeAndRoutingKey() {
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> OutgoingMessage.to(null, "routing-key")).withMessage("exchange");
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> OutgoingMessage.to("exchange", null)).withMessage("routingKey");
	}

	@Test
	void headersCannotBeChangedFromOutside() {
		final var headers = OutgoingMessage.to("exchange", "routing-key").withHeader("x-first", "one").getHeaders();

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> headers.put("x-second", "two"));
	}
}
