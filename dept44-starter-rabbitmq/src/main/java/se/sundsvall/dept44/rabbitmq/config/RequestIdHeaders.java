package se.sundsvall.dept44.rabbitmq.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static java.util.Optional.ofNullable;

/**
 * Sends the request id and the identifier of the user along with every message, as headers under the same names Feign
 * and WebClient use over HTTP. A header the service has set itself is left as it is.
 */
class RequestIdHeaders implements MessagePostProcessor {

	@Override
	public Message postProcessMessage(final Message message) {
		final var headers = message.getMessageProperties().getHeaders();
		ofNullable(RequestId.get()).ifPresent(requestId -> headers.putIfAbsent(RequestId.HEADER_NAME, requestId));
		ofNullable(Identifier.get())
			.map(Identifier::toHeaderValue)
			.ifPresent(identifier -> headers.putIfAbsent(Identifier.HEADER_NAME, identifier));
		return message;
	}
}
