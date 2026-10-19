package se.sundsvall.dept44.rabbitmq;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.amqp.core.Address;

import static java.util.Objects.requireNonNull;

/**
 * A message for {@link Dept44RabbitPublisher}: where it goes, what it carries and the AMQP properties the receiver
 * needs.
 * <p>
 * The payload is converted by the application's {@code MessageConverter}, which is JSON unless the service replaces it.
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
	private final Map<String, Object> headers = new LinkedHashMap<>();
	private Object payload;
	private String messageId;
	private String correlationId;
	private Address replyTo;

	private OutgoingMessage(final String exchange, final String routingKey) {
		this.exchange = requireNonNull(exchange, "exchange");
		this.routingKey = requireNonNull(routingKey, "routingKey");
	}

	/**
	 * @param  exchange   the exchange to publish to
	 * @param  routingKey the routing key, empty for an exchange that does not route on it
	 * @return            a message to that exchange, with no payload yet
	 */
	public static OutgoingMessage to(final String exchange, final String routingKey) {
		return new OutgoingMessage(exchange, routingKey);
	}

	public OutgoingMessage withPayload(final Object payload) {
		this.payload = payload;
		return this;
	}

	/**
	 * Optional, the publisher generates one when absent. Set it when the receiver must be able to recognize a duplicate,
	 * such as when the same message may be published again: a publish that ends in a timeout may still have reached the
	 * broker.
	 */
	public OutgoingMessage withMessageId(final String messageId) {
		this.messageId = messageId;
		return this;
	}

	/**
	 * Copied unchanged to a reply. What it holds is up to the sender.
	 */
	public OutgoingMessage withCorrelationId(final String correlationId) {
		this.correlationId = correlationId;
		return this;
	}

	/**
	 * Where a reply goes: an exchange of the responder's and the routing key the sender's reply queue is bound with.
	 * Sent as {@code exchange/routingKey}, the format Spring AMQP reads a reply address in.
	 */
	public OutgoingMessage withReplyTo(final String exchange, final String routingKey) {
		this.replyTo = new Address(exchange, routingKey);
		return this;
	}

	public OutgoingMessage withHeader(final String name, final Object value) {
		headers.put(name, value);
		return this;
	}

	public String getExchange() {
		return exchange;
	}

	public String getRoutingKey() {
		return routingKey;
	}

	public Object getPayload() {
		return payload;
	}

	public String getMessageId() {
		return messageId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public Address getReplyTo() {
		return replyTo;
	}

	public Map<String, Object> getHeaders() {
		return Collections.unmodifiableMap(headers);
	}
}
