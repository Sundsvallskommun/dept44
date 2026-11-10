package se.sundsvall.dept44.rabbitmq;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static java.lang.Boolean.FALSE;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.BLOCKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.INTERRUPTED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.NACKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.RETURNED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.TIMEOUT;

/**
 * Publishes a message and returns once the broker has taken responsibility for it, or throws
 * {@link RabbitPublishException}.
 * <p>
 * The broker has taken responsibility when it has confirmed the message and not returned it as unroutable. A publish
 * that does not wait for that can lose the message without anyone noticing: on a nack, on an exchange no queue is bound
 * to, or on a connection that drops.
 * <p>
 * While the broker blocks publishing, during a memory or disk alarm, a publish fails at once instead of waiting in the
 * socket until the alarm clears. The broker blocks a connection once it publishes during the alarm, so the first
 * publish of an alarm ends in a timeout.
 * <p>
 * Each publish waits for a round trip to the broker. A message the broker has confirmed is logged, without its body,
 * on the logger the listeners log their messages on.
 */
public class Dept44RabbitPublisher {

	private static final Logger LOG = LoggerFactory.getLogger("se.sundsvall.dept44.rabbitmq.messages");
	private static final Message PROBE = new Message(new byte[0]);

	private final RabbitTemplate rabbitTemplate;
	private final Duration confirmTimeout;
	private final BlockedConnections blockedConnections = new BlockedConnections();

	/**
	 * @param  rabbitTemplate           the template to publish with, on a connection of its own with correlated publisher
	 *                                  confirms and returns, and mandatory set
	 * @param  confirmTimeout           how long to wait for the broker to confirm a message
	 * @throws IllegalArgumentException if the timeout is not positive: every publish would time out, also those the
	 *                                  broker took
	 * @throws IllegalStateException    if the template publishes on the listeners' connection, or cannot tell whether
	 *                                  the broker took the message
	 */
	public Dept44RabbitPublisher(final RabbitTemplate rabbitTemplate, final Duration confirmTimeout) {
		if (isNull(confirmTimeout) || !confirmTimeout.isPositive()) {
			throw new IllegalArgumentException("The confirm timeout must be positive, was " + confirmTimeout);
		}
		final var connectionFactory = publishingConnectionFactory(rabbitTemplate);
		requireConfirmsAndReturns(rabbitTemplate, connectionFactory);
		this.rabbitTemplate = rabbitTemplate;
		this.confirmTimeout = confirmTimeout;
		connectionFactory.addConnectionListener(blockedConnections);
	}

	/**
	 * Publishes and waits for the broker to confirm. A message without a message id gets a generated one.
	 * <p>
	 * After a {@code TIMEOUT} or an {@code INTERRUPTED} the broker may already have the message, so publishing it again
	 * can deliver it twice. Publish it again with the same message id, for the receiver to recognize the duplicate.
	 *
	 * @throws RabbitPublishException if the broker nacks or returns the message, does not confirm it in time, or blocks
	 *                                publishing
	 * @throws AmqpException          if the message could not be sent at all, such as when the broker cannot be reached
	 */
	public void publish(final OutgoingMessage message) {
		send(message);
	}

	/**
	 * As {@link #publish(OutgoingMessage)}, for a request that expects a reply. The responder replies to the reply-to
	 * address and copies the correlation id, and the message id lets it recognize a duplicate. It does not wait for the
	 * reply, which reaches the sender's listener on its reply queue.
	 *
	 * @throws IllegalArgumentException if the message lacks a message id, a correlation id or a reply-to address
	 */
	public void request(final OutgoingMessage message) {
		final var missing = new ArrayList<String>();
		if (isNull(message.getMessageId())) {
			missing.add("message id");
		}
		if (isNull(message.getCorrelationId())) {
			missing.add("correlation id");
		}
		if (isNull(message.getReplyTo())) {
			missing.add("reply-to");
		}
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("A request to exchange '%s' lacks %s".formatted(message.getExchange(), String.join(", ", missing)));
		}
		send(message);
	}

	private void send(final OutgoingMessage message) {
		if (isNull(message.getPayload())) {
			throw new IllegalArgumentException("A message to exchange '%s' has no payload".formatted(message.getExchange()));
		}
		final var messageId = ofNullable(message.getMessageId()).orElseGet(() -> UUID.randomUUID().toString());
		final var description = describe(message, messageId);
		final var blockedReason = blockedConnections.reason();
		if (blockedReason.isPresent()) {
			throw new RabbitPublishException(BLOCKED, messageId, "%s was not sent, the broker blocks publishing: %s".formatted(description, blockedReason.get()));
		}
		// An id of its own: two publishes of the same message id must not share a confirm
		final var correlationData = new CorrelationData(UUID.randomUUID().toString());

		rabbitTemplate.convertAndSend(message.getExchange(), message.getRoutingKey(), message.getPayload(), amqpMessage -> {
			final var properties = amqpMessage.getMessageProperties();
			properties.setMessageId(messageId);
			properties.setCorrelationId(message.getCorrelationId());
			if (nonNull(message.getReplyTo())) {
				properties.setReplyToAddress(message.getReplyTo());
			}
			message.getHeaders().forEach(properties::setHeader);
			return amqpMessage;
		}, correlationData);

		awaitConfirm(messageId, description, correlationData);
		LOG.info("{} was confirmed by the broker", description);
	}

	private void awaitConfirm(final String messageId, final String description, final CorrelationData correlationData) {
		final CorrelationData.Confirm confirm;
		try {
			confirm = correlationData.getFuture().get(confirmTimeout.toNanos(), NANOSECONDS);
		} catch (final TimeoutException _) {
			throw new RabbitPublishException(TIMEOUT, messageId, "%s was not confirmed within %s".formatted(description, confirmTimeout));
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RabbitPublishException(INTERRUPTED, messageId, "Interrupted while waiting for the confirm of %s".formatted(description), e);
		} catch (final ExecutionException e) {
			// Spring AMQP completes the future with a nack, not exceptionally, when the channel closes
			throw new RabbitPublishException(NACKED, messageId, "%s was not confirmed".formatted(description), e.getCause());
		}

		if (!confirm.ack()) {
			throw new RabbitPublishException(NACKED, messageId, "%s was nacked by the broker: %s".formatted(description, confirm.reason()));
		}
		// The broker sends a return before the ack, and Spring AMQP sets it before it completes the future
		final var returned = correlationData.getReturned();
		if (nonNull(returned)) {
			throw new RabbitPublishException(RETURNED, messageId, "%s was returned by the broker: %d %s".formatted(description, returned.getReplyCode(), returned.getReplyText()));
		}
	}

	/**
	 * The connection factory the template publishes on: its publisher connection factory, when it has one.
	 * <p>
	 * On the listeners' connection a publish during a memory or disk alarm would get their acks blocked too, and the
	 * alarm would never clear. The starter sets every {@code RabbitTemplate} bean to publish on a connection of its own,
	 * but never sees a template created with {@code new} outside a bean.
	 */
	private static ConnectionFactory publishingConnectionFactory(final RabbitTemplate rabbitTemplate) {
		if (!rabbitTemplate.isUsePublisherConnection()) {
			throw new IllegalStateException("Dept44RabbitPublisher requires a RabbitTemplate that publishes on a connection of its own. "
				+ "The starter sets that on every RabbitTemplate bean; a template created outside a bean needs setUsePublisherConnection(true)");
		}
		final var connectionFactory = rabbitTemplate.getConnectionFactory();
		return ofNullable(connectionFactory.getPublisherConnectionFactory()).orElse(connectionFactory);
	}

	private static String describe(final OutgoingMessage message, final String messageId) {
		return "Message %s to exchange '%s' with routing key '%s'".formatted(messageId, message.getExchange(), message.getRoutingKey());
	}

	/**
	 * Without confirms the wait never ends, and without returns or mandatory an unroutable message is confirmed and
	 * dropped. Each of these is a default of the starter, so this only fails when a service has overridden one. Checked
	 * on the connection factory the template publishes on.
	 */
	private static void requireConfirmsAndReturns(final RabbitTemplate rabbitTemplate, final ConnectionFactory connectionFactory) {
		if (!connectionFactory.isPublisherConfirms()) {
			throw new IllegalStateException("Dept44RabbitPublisher requires spring.rabbitmq.publisher-confirm-type=correlated");
		}
		if (!connectionFactory.isPublisherReturns()) {
			throw new IllegalStateException("Dept44RabbitPublisher requires spring.rabbitmq.publisher-returns=true");
		}
		if (FALSE.equals(rabbitTemplate.isMandatoryFor(PROBE))) {
			throw new IllegalStateException("Dept44RabbitPublisher requires spring.rabbitmq.template.mandatory=true");
		}
	}
}
