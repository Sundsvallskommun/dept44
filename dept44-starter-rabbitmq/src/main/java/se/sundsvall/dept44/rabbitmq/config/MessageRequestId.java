package se.sundsvall.dept44.rabbitmq.config;

import java.util.Objects;
import org.springframework.amqp.core.Message;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static java.util.Optional.ofNullable;

/**
 * Makes the request id and the identifier a message was sent with the current thread's, for as long as the message is
 * handled. A message without a request id gets a new one, as an HTTP request does.
 */
final class MessageRequestId {

	private MessageRequestId() {}

	/**
	 * @param message the message, or null when there is none to take them from
	 */
	static void set(final Message message) {
		RequestId.init(header(message, RequestId.HEADER_NAME));
		Identifier.set(Identifier.parse(header(message, Identifier.HEADER_NAME)));
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
