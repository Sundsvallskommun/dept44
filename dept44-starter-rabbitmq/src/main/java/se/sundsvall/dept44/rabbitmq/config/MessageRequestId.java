package se.sundsvall.dept44.rabbitmq.config;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.Message;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * Makes the request id and the identifier a message was sent with the current thread's, for as long as the message is
 * handled. A message without a request id gets a new one, as an HTTP request does.
 * <p>
 * A request id goes on as a header on every call the listener makes through Feign, and on every message it publishes.
 * OkHttp fails a call on a header character outside printable ASCII, and the starter also caps a request id at 128
 * characters and refuses spaces in it. A request id that is not such a string gets the message a new one. The
 * identifier is kept as it came, as over HTTP. One with a character outside printable ASCII fails the listener's Feign
 * calls rather than have them made without the user. A header that is not a string, such as a {@code byte[]} from a
 * producer, counts as none.
 */
final class MessageRequestId {

	// What a delivery that failed was handled under, for the container's error handler
	private static final ThreadLocal<@Nullable HandledUnder> FAILED = new ThreadLocal<>();
	private static final Pattern VALID_REQUEST_ID = Pattern.compile("[\\x21-\\x7e]{1,128}");

	private MessageRequestId() {}

	/**
	 * @param message the message, or null when there is none to take them from
	 */
	static void set(final @Nullable Message message) {
		set(header(message, RequestId.HEADER_NAME).filter(requestId -> VALID_REQUEST_ID.matcher(requestId).matches()).orElse(null),
			header(message, Identifier.HEADER_NAME).orElse(null));
	}

	/**
	 * Keeps the current request id and identifier, those of a delivery that failed. A message without a request id, and
	 * a batch, were handled under a new one that no message carries. The container calls its error handler once the
	 * advice has cleared them, see {@link #setForErrorHandler(Message)}.
	 */
	static void keepFailed() {
		FAILED.set(new HandledUnder(RequestId.get(), Identifier.get()));
	}

	/**
	 * Forgets a failed delivery that no error handler has taken the request id of, before the next delivery.
	 */
	static void forgetFailed() {
		FAILED.remove();
	}

	/**
	 * As {@link #set(Message)}, but with what the delivery that just failed was handled under, when it was kept.
	 */
	static void setForErrorHandler(final @Nullable Message failedMessage) {
		final var handledUnder = FAILED.get();
		FAILED.remove();
		if (nonNull(handledUnder)) {
			RequestId.init(handledUnder.requestId());
			Identifier.set(handledUnder.identifier());
		} else {
			set(failedMessage);
		}
	}

	private static void set(final @Nullable String requestId, final @Nullable String identifier) {
		RequestId.init(requestId);
		Identifier.set(Identifier.parse(identifier));
	}

	static void clear() {
		RequestId.reset();
		Identifier.remove();
	}

	private record HandledUnder(String requestId, @Nullable Identifier identifier) {
	}

	private static Optional<String> header(final @Nullable Message message, final String name) {
		return ofNullable(message)
			.map(m -> m.getMessageProperties().getHeader(name))
			.filter(String.class::isInstance)
			.map(String.class::cast);
	}
}
