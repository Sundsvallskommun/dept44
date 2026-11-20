package se.sundsvall.dept44.rabbitmq;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
	@SuppressWarnings("DataFlowIssue")
	void requiresExchangeAndRoutingKey() {
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> OutgoingMessage.to(null, "routing-key")).withMessage("exchange");
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> OutgoingMessage.to("exchange", null)).withMessage("routingKey");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		" ", "\t"
	})
	@SuppressWarnings("DataFlowIssue")
	void refusesABlankMessageIdOrCorrelationId(final @Nullable String id) {
		final var message = OutgoingMessage.to("exchange", "routing-key");

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> message.withMessageId(id)).withMessage("The message id must not be blank");
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> message.withCorrelationId(id)).withMessage("The correlation id must not be blank");
	}

	@Test
	void eachWithReturnsACopyAndLeavesTheMessageItCameFromAsItWas() {
		final var base = OutgoingMessage.to("exchange", "routing-key").withHeader("x-first", "one");

		final var derived = base.withPayload("payload")
			.withMessageId("message-id")
			.withCorrelationId("correlation-id")
			.withReplyTo("reply-exchange", "reply-key")
			.withHeader("x-second", "two");

		assertThat(derived).isNotSameAs(base);
		assertThat(base.getPayload()).isNull();
		assertThat(base.getMessageId()).isNull();
		assertThat(base.getCorrelationId()).isNull();
		assertThat(base.getReplyTo()).isNull();
		assertThat(base.getHeaders()).containsExactly(entry("x-first", "one"));
		assertThat(derived.getHeaders()).containsExactly(entry("x-first", "one"), entry("x-second", "two"));
	}

	@Test
	void headersCannotBeChangedFromOutside() {
		final var headers = OutgoingMessage.to("exchange", "routing-key").withHeader("x-first", "one").getHeaders();

		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> headers.put("x-second", "two"));
	}
}
