package se.sundsvall.dept44.rabbitmq;

import org.springframework.amqp.AmqpException;

/**
 * Thrown by {@link Dept44RabbitPublisher} when the broker has not taken responsibility for a message. What that means
 * to the service, such as an error to its caller or a message left for redelivery, is up to the service.
 */
public class RabbitPublishException extends AmqpException {

	private static final long serialVersionUID = 1L;

	private final Reason reason;
	private final String messageId;

	public RabbitPublishException(final Reason reason, final String messageId, final String message) {
		super(message);
		this.reason = reason;
		this.messageId = messageId;
	}

	public RabbitPublishException(final Reason reason, final String messageId, final String message, final Throwable cause) {
		super(message, cause);
		this.reason = reason;
		this.messageId = messageId;
	}

	public Reason getReason() {
		return reason;
	}

	/**
	 * @return the message id the message was published with, the generated one when the service set none
	 */
	public String getMessageId() {
		return messageId;
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
