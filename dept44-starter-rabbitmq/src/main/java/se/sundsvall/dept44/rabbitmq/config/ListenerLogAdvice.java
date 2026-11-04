package se.sundsvall.dept44.rabbitmq.config;

import java.util.Arrays;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.NoOpLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ContainerUtils;

import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.joining;

/**
 * Logs a line for each message a listener has handled: its message id, queue, routing key and delivery count, what
 * becomes of it and how long it took. Not the body or the headers, which may hold personal data.
 * <p>
 * Comes after {@link RequestIdListenerAdvice} in the advice chain, so that the line has the message's request id, and
 * before the retry, so that a message gets a line for each delivery rather than for each attempt. What becomes of the
 * message is read from what leaves the chain, as the container reads it. By then {@link TransientFailureRecoverer} has
 * turned every failure into an acknowledgement, a requeue or a reject. Without it, as with the retry off, a failure is
 * logged as failed, and the container decides.
 */
class ListenerLogAdvice implements MethodInterceptor {

	private static final Logger LOG = LoggerFactory.getLogger("se.sundsvall.dept44.rabbitmq.messages");
	private static final String DELIVERY_COUNT = "x-delivery-count";
	// shouldRequeue logs that it rejects the message, which the advice does not
	private static final Log NO_LOG = new NoOpLog();

	@Override
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		final var start = System.nanoTime();
		var outcome = Outcome.FAILED;
		try {
			final var result = invocation.proceed();
			outcome = Outcome.ACKNOWLEDGED;
			return result;
		} catch (final RuntimeException e) {
			outcome = outcomeOf(e);
			throw e;
		} finally {
			log(invocation.getArguments(), outcome, NANOSECONDS.toMillis(System.nanoTime() - start));
		}
	}

	/**
	 * As the container decides, with its own checks: an acknowledgement first, then the first of a requeue and a reject
	 * in the cause chain. A failure with neither is left to the container's default, which the advice cannot see.
	 */
	private static Outcome outcomeOf(final Throwable failure) {
		if (ContainerUtils.isImmediateAcknowledge(failure)) {
			return Outcome.ACKNOWLEDGED;
		}
		if (ContainerUtils.shouldRequeue(false, failure, NO_LOG)) {
			return Outcome.REQUEUED;
		}
		return ContainerUtils.isAmqpReject(failure) ? Outcome.REJECTED : Outcome.FAILED;
	}

	/**
	 * The container passes the channel and the message, or a list of them for a batch listener
	 */
	private static void log(final Object[] arguments, final Outcome outcome, final long millis) {
		if (!LOG.isInfoEnabled()) {
			return;
		}
		final var data = Arrays.stream(arguments)
			.filter(argument -> argument instanceof Message || argument instanceof List)
			.findFirst()
			.orElse(null);

		if (data instanceof final Message message) {
			final var properties = message.getMessageProperties();
			// Absent on the first delivery. A Long from the broker
			final var deliveryCount = properties.<Object>getHeader(DELIVERY_COUNT);
			LOG.info("Message {} from queue '{}' with routing key '{}' (delivery count {}, {} ms) {}", properties.getMessageId(), properties.getConsumerQueue(),
				properties.getReceivedRoutingKey(), requireNonNullElse(deliveryCount, 0), millis, outcome.description);
		} else if (data instanceof final List<?> batch && !batch.isEmpty() && batch.getFirst() instanceof final Message first) {
			final var messageIds = batch.stream()
				.map(Message.class::cast)
				.map(message -> String.valueOf(message.getMessageProperties().getMessageId()))
				.collect(joining(", "));
			LOG.info("Batch of messages {} from queue '{}' ({} ms) {}", messageIds, first.getMessageProperties().getConsumerQueue(), millis, outcome.description);
		}
	}

	private enum Outcome {

		ACKNOWLEDGED("was acknowledged"),
		REQUEUED("was put back in the queue, which dead-letters it past its x-delivery-limit"),
		REJECTED("was rejected, for the queue to dead-letter"),
		FAILED("failed");

		private final String description;

		Outcome(final String description) {
			this.description = description;
		}
	}
}
