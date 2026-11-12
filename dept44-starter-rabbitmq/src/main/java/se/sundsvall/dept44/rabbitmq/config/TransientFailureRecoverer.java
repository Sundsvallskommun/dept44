package se.sundsvall.dept44.rabbitmq.config;

import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.NoOpLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ContainerUtils;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.MessageBatchRecoverer;
import org.springframework.core.NestedExceptionUtils;
import se.sundsvall.dept44.rabbitmq.TransientFailures;

import static java.util.stream.Collectors.joining;

/**
 * Decides what becomes of a message the listener could not handle, once its retries are over. An outage, as the
 * {@link TransientFailures} bean tells it, puts the message back in the queue, where it counts against the queue's
 * delivery limit, so that a longer outage is waited out as well. So does a listener that was interrupted, as when the
 * service shuts down. A listener that threw {@link AmqpRejectAndDontRequeueException},
 * {@link ImmediateRequeueAmqpException} or {@link ImmediateAcknowledgeAmqpException} gets what it asked for, also when
 * it was interrupted, except a batch listener that asks for its batch back. Any other failure, a server error that
 * outlasted its retries included, is rejected, and the queue dead-letters it.
 * <p>
 * A batch listener's batch is handled as one: Spring AMQP acknowledges a batch whose recoverer does nothing with it. A
 * batch in an outage is rejected rather than put back, and so is a batch whose listener threw
 * {@link ImmediateRequeueAmqpException}, with a WARN line. Spring AMQP puts a batch back with a {@code basic.nack},
 * which RabbitMQ from 4.3 does not count against the delivery limit, so the batch would come back at once, for as long
 * as the outage lasts or for as long as the listener asks. A batch listener that was interrupted, as when the service
 * shuts down, still gets its batch back: the consumer is stopping, so the batch comes back once and not in a loop.
 * <p>
 * Each outcome is thrown inside a {@link ListenerExecutionFailedException}. The container carries on with the next
 * message after one, and restarts its consumer after anything else.
 * <p>
 * Spring AMQP's own recoverers log the whole message, body included. This one logs its id, its queue and the root cause
 * of the failure. The root cause's message may quote a value from the body, as Jackson's does for a value it cannot
 * read, and so does the stack trace the container's error handler logs.
 */
class TransientFailureRecoverer implements MessageBatchRecoverer {

	private static final Logger LOG = LoggerFactory.getLogger(TransientFailureRecoverer.class);
	// shouldRequeue debug-logs "Rejecting messages", which is only true when the container calls it
	private static final Log NO_LOG = new NoOpLog();

	private final TransientFailures transientFailures;

	TransientFailureRecoverer(final TransientFailures transientFailures) {
		this.transientFailures = transientFailures;
	}

	@Override
	public void recover(final Message message, final Throwable cause) {
		throw outcome(List.of(message), cause, false);
	}

	@Override
	public void recover(final List<Message> messages, final Throwable cause) {
		throw outcome(messages, cause, true);
	}

	private ListenerExecutionFailedException outcome(final List<Message> messages, final Throwable cause, final boolean batch) {
		final var failed = messages.toArray(Message[]::new);
		final var messageIds = messages.stream().map(message -> String.valueOf(message.getMessageProperties().getMessageId())).collect(joining(", "));
		final var queue = messages.getFirst().getMessageProperties().getConsumerQueue();
		final var rootCause = NestedExceptionUtils.getMostSpecificCause(cause).toString();

		if (decidedByTheListener(cause)) {
			if (batch && asksToPutBack(cause) && !wasInterrupted(cause)) {
				LOG.warn("The listener asked to put back the batch of messages {} from queue {}. Rejecting it for the queue to dead-letter instead, "
					+ "since a batch goes back with a basic.nack that RabbitMQ does not count against x-delivery-limit: {}", messageIds, queue, rootCause);
				return new ListenerExecutionFailedException("Rejected without requeue, a batch is not put back", new AmqpRejectAndDontRequeueException(cause), failed);
			}
			return new ListenerExecutionFailedException("Handled as the listener asked", cause, failed);
		}
		if (wasInterrupted(cause)) {
			LOG.warn("Interrupted while handling message {} from queue {}, putting it back in the queue, which dead-letters it past its x-delivery-limit: {}", messageIds, queue, rootCause);
			return new ListenerExecutionFailedException("Interrupted, put back in the queue", new ImmediateRequeueAmqpException(cause), failed);
		}
		if (!batch && transientFailures.isOutage(cause)) {
			LOG.warn("Retries exhausted for message {} from queue {}, putting it back in the queue, which dead-letters it past its x-delivery-limit: {}", messageIds, queue, rootCause);
			return new ListenerExecutionFailedException("Retries exhausted, put back in the queue", new ImmediateRequeueAmqpException(cause), failed);
		}
		LOG.warn("Rejecting message {} from queue {}, for the queue to dead-letter: {}", messageIds, queue, rootCause);
		return new ListenerExecutionFailedException("Rejected without requeue", new AmqpRejectAndDontRequeueException(cause), failed);
	}

	/**
	 * A listener that threw one of these has said what becomes of the message, whatever it wraps.
	 */
	private static boolean decidedByTheListener(final Throwable cause) {
		return ExceptionUtils.getThrowableList(cause).stream().anyMatch(throwable -> throwable instanceof AmqpRejectAndDontRequeueException
			|| throwable instanceof ImmediateRequeueAmqpException
			|| throwable instanceof ImmediateAcknowledgeAmqpException);
	}

	/**
	 * Whether the container puts back what the listener threw, by its own checks: no acknowledgement, and a requeue before
	 * any reject in the cause chain.
	 */
	private static boolean asksToPutBack(final Throwable cause) {
		return !ListenerLogAdvice.acknowledgedByTheContainer(cause) && ContainerUtils.shouldRequeue(false, cause, NO_LOG);
	}

	/**
	 * The interrupt flag, or an {@link InterruptedException} from a listener that caught it and threw it on wrapped
	 * without interrupting itself again.
	 */
	private static boolean wasInterrupted(final Throwable cause) {
		return Thread.currentThread().isInterrupted() || ExceptionUtils.indexOfType(cause, InterruptedException.class) != -1;
	}
}
