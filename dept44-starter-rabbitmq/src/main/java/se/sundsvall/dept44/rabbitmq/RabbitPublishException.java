package se.sundsvall.dept44.rabbitmq;

import org.springframework.amqp.AmqpException;

/**
 * Thrown by {@link Dept44RabbitPublisher} when the broker has not taken responsibility for a message. What that means
 * to the service, such as an error to its caller or a message left for redelivery, is up to the service.
 */
public class RabbitPublishException extends AmqpException {

	private static final long serialVersionUID = 1L;

	private final Reason reason;

	public RabbitPublishException(final Reason reason, final String message) {
		super(message);
		this.reason = reason;
	}

	public RabbitPublishException(final Reason reason, final String message, final Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

	public enum Reason {
		/** The broker refused the message, or the channel closed before the broker confirmed it. */
		NACKED,
		/** The broker could not route the message to any queue. */
		RETURNED,
		/** No confirm within the timeout. The broker may still have the message. */
		TIMEOUT,
		/** The thread was interrupted while waiting for the confirm. The broker may still have the message. */
		INTERRUPTED
	}
}
