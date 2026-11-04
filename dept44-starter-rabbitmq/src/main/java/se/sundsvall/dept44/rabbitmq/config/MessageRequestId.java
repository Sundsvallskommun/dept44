package se.sundsvall.dept44.rabbitmq.config;

import java.util.Objects;
import org.springframework.amqp.core.Message;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * Makes the request id and the identifier a message was sent with the current thread's, for as long as the message is
 * handled. A message without a request id gets a new one, as an HTTP request does.
 */
final class MessageRequestId {

	// The request id a batch that failed was handled under, for the container's error handler
	private static final ThreadLocal<String> FAILED_BATCH = new ThreadLocal<>();

	private MessageRequestId() {}

	/**
	 * @param message the message, or null when there is none to take them from
	 */
	static void set(final Message message) {
		set(header(message, RequestId.HEADER_NAME), header(message, Identifier.HEADER_NAME));
	}

	/**
	 * Keeps the current request id, that of a batch that failed. The container calls its error handler once the advice
	 * has cleared it, see {@link #setForErrorHandler(Message)}.
	 */
	static void keepFailedBatch() {
		FAILED_BATCH.set(RequestId.get());
	}

	/**
	 * Forgets a failed batch that no error handler has taken the request id of, before the next delivery.
	 */
	static void forgetFailedBatch() {
		FAILED_BATCH.remove();
	}

	/**
	 * As {@link #set(Message)}, but with the request id of the batch that just failed, when one has.
	 */
	static void setForErrorHandler(final Message failedMessage) {
		final var batchRequestId = FAILED_BATCH.get();
		FAILED_BATCH.remove();
		if (nonNull(batchRequestId)) {
			set(batchRequestId, null);
		} else {
			set(failedMessage);
		}
	}

	private static void set(final String requestId, final String identifier) {
		RequestId.init(requestId);
		Identifier.set(Identifier.parse(identifier));
	}

	static void clear() {
		RequestId.reset();
		Identifier.remove();
	}

	private static String header(final Message message, final String name) {
		return ofNullable(message)
			.map(m -> m.getMessageProperties().getHeader(name))
			.map(Objects::toString)
			.orElse(null);
	}
}
