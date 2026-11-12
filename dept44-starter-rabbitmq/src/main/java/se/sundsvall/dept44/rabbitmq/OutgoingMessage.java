package se.sundsvall.dept44.rabbitmq;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.Address;
import org.springframework.util.Assert;

import static java.util.Objects.requireNonNull;

/**
 * A message for {@link Dept44RabbitPublisher}: where it goes, what it carries and the AMQP properties the receiver
 * needs.
 * <p>
 * The payload is converted by the application's {@code MessageConverter}, which is JSON unless the service replaces it.
 * <p>
 * Immutable: each {@code with} method returns a new message and leaves the one it was called on as it was, so a message
 * can be shared between threads and serve as the base for others.
 *
 * <pre>{@code
 * OutgoingMessage.to("my-exchange", "my-routing-key")
 * 	.withPayload(payload)
 * 	.withMessageId(id)
 * 	.withCorrelationId(correlationId)
 * 	.withHeader("x-custom", value);
 * }</pre>
 */
public final class OutgoingMessage {

	private final String exchange;
	private final String routingKey;
	private final @Nullable Object payload;
	private final @Nullable String messageId;
	private final @Nullable String correlationId;
	private final @Nullable Address replyTo;
	// Never changed once the message has it, which lets the messages derived from it share it. withHeader copies it
	private final Map<String, Object> headers;

	private OutgoingMessage(final String exchange, final String routingKey, final @Nullable Object payload, final @Nullable String messageId, final @Nullable String correlationId,
		final @Nullable Address replyTo, final Map<String, Object> headers) {
		this.exchange = requireNonNull(exchange, "exchange");
		this.routingKey = requireNonNull(routingKey, "routingKey");
		this.payload = payload;
		this.messageId = messageId;
		this.correlationId = correlationId;
		this.replyTo = replyTo;
		this.headers = headers;
	}

	/**
	 * @param  exchange   the exchange to publish to
	 * @param  routingKey the routing key, empty for an exchange that does not route on it
	 * @return            a message to that exchange, with no payload yet
	 */
	public static OutgoingMessage to(final String exchange, final String routingKey) {
		return new OutgoingMessage(exchange, routingKey, null, null, null, null, new LinkedHashMap<>());
	}

	public OutgoingMessage withPayload(final Object payload) {
		return new OutgoingMessage(exchange, routingKey, payload, messageId, correlationId, replyTo, headers);
	}

	/**
	 * Optional, the publisher generates one when absent. Set it when the receiver must be able to recognize a duplicate,
	 * such as when the same message may be published again: a publish that ends in a timeout may still have reached the
	 * broker.
	 *
	 * @throws IllegalArgumentException if the id is null or blank. The messages published with a blank id could not be
	 *                                  told apart.
	 */
	public OutgoingMessage withMessageId(final String messageId) {
		Assert.hasText(messageId, "The message id must not be blank");
		return new OutgoingMessage(exchange, routingKey, payload, messageId, correlationId, replyTo, headers);
	}

	/**
	 * Copied unchanged to a reply. What it holds is up to the sender. A responder copies it only when the request has one.
	 *
	 * @throws IllegalArgumentException if the id is null or blank
	 */
	public OutgoingMessage withCorrelationId(final String correlationId) {
		Assert.hasText(correlationId, "The correlation id must not be blank");
		return new OutgoingMessage(exchange, routingKey, payload, messageId, correlationId, replyTo, headers);
	}

	/**
	 * Where a reply goes: an exchange of the responder's and the routing key the sender's reply queue is bound with.
	 * Sent as {@code exchange/routingKey}, the format Spring AMQP reads a reply address in.
	 */
	public OutgoingMessage withReplyTo(final String exchange, final String routingKey) {
		return new OutgoingMessage(this.exchange, this.routingKey, payload, messageId, correlationId, new Address(exchange, routingKey), headers);
	}

	public OutgoingMessage withHeader(final String name, final Object value) {
		final var copy = new LinkedHashMap<>(headers);
		copy.put(name, value);
		return new OutgoingMessage(exchange, routingKey, payload, messageId, correlationId, replyTo, copy);
	}

	public String getExchange() {
		return exchange;
	}

	public String getRoutingKey() {
		return routingKey;
	}

	public @Nullable Object getPayload() {
		return payload;
	}

	public @Nullable String getMessageId() {
		return messageId;
	}

	public @Nullable String getCorrelationId() {
		return correlationId;
	}

	public @Nullable Address getReplyTo() {
		return replyTo;
	}

	public Map<String, Object> getHeaders() {
		return Collections.unmodifiableMap(headers);
	}
}
