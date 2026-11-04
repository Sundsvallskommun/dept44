package se.sundsvall.dept44.rabbitmq.config;

import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.MessageBatchRecoverer;
import org.springframework.core.NestedExceptionUtils;
import se.sundsvall.dept44.rabbitmq.TransientFailures;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

/**
 * Decides what becomes of a message the listener could not handle, once its retries are over. An outage puts the
 * message back in the queue, where it counts against the queue's delivery limit, so that a longer outage is waited out
 * as well. So does a listener that was interrupted, as when the service shuts down. A listener that threw
 * {@link ImmediateRequeueAmqpException} or {@link ImmediateAcknowledgeAmqpException} gets what it asked for. Any other
 * failure, a server error that outlasted its retries included, is rejected, and the queue dead-letters it. See
 * {@link TransientFailures}.
 * <p>
 * A batch listener's batch is handled as one: Spring AMQP acknowledges a batch whose recoverer does nothing with it.
 * <p>
 * Each outcome is thrown inside a {@link ListenerExecutionFailedException}. The container carries on with the next
 * message after one, and restarts its consumer after anything else.
 * <p>
 * Spring AMQP's own recoverers log the whole message, body included. This one logs its id and queue, and the
 * container's error handler logs the stack trace.
 */
class TransientFailureRecoverer implements MessageBatchRecoverer {

	private static final Logger LOG = LoggerFactory.getLogger(TransientFailureRecoverer.class);

	@Override
	public void recover(final Message message, final Throwable cause) {
		recover(List.of(message), cause);
	}

	@Override
	public void recover(final List<Message> messages, final Throwable cause) {
		final var failed = messages.toArray(Message[]::new);
		final var messageIds = messages.stream().map(message -> String.valueOf(message.getMessageProperties().getMessageId())).collect(joining(", "));
		final var queue = messages.getFirst().getMessageProperties().getConsumerQueue();
		final var rootCause = ofNullable(cause).map(NestedExceptionUtils::getMostSpecificCause).map(Throwable::toString).orElse(null);

		if (wasInterrupted(cause)) {
			LOG.warn("Interrupted while handling message {} from queue {}, putting it back in the queue, which dead-letters it past its x-delivery-limit: {}", messageIds, queue, rootCause);
			throw new ListenerExecutionFailedException("Interrupted, put back in the queue", new ImmediateRequeueAmqpException(cause), failed);
		}
		if (TransientFailures.isOutage(cause)) {
			LOG.warn("Retries exhausted for message {} from queue {}, putting it back in the queue, which dead-letters it past its x-delivery-limit: {}", messageIds, queue, rootCause);
			throw new ListenerExecutionFailedException("Retries exhausted, put back in the queue", new ImmediateRequeueAmqpException(cause), failed);
		}
		if (askedForRequeueOrAcknowledge(cause)) {
			throw new ListenerExecutionFailedException("Handled as the listener asked", cause, failed);
		}
		LOG.warn("Rejecting message {} from queue {}, for the queue to dead-letter: {}", messageIds, queue, rootCause);
		throw new ListenerExecutionFailedException("Rejected without requeue", new AmqpRejectAndDontRequeueException(cause), failed);
	}

	/**
	 * The interrupt flag, or an {@link InterruptedException} from a listener that caught it and threw it on wrapped
	 * without interrupting itself again.
	 */
	private static boolean wasInterrupted(final Throwable cause) {
		return Thread.currentThread().isInterrupted() || ExceptionUtils.indexOfType(cause, InterruptedException.class) != -1;
	}

	private static boolean askedForRequeueOrAcknowledge(final Throwable cause) {
		return ExceptionUtils.indexOfType(cause, ImmediateRequeueAmqpException.class) != -1
			|| ExceptionUtils.indexOfType(cause, ImmediateAcknowledgeAmqpException.class) != -1;
	}
}
