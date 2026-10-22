package se.sundsvall.dept44.rabbitmq.config;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RequestIdErrorHandlerTest {

	@Test
	void handlesTheFailureUnderTheRequestIdOfTheFailedMessage() {
		final var seen = new AtomicReference<String>();
		final var seenIdentifier = new AtomicReference<Identifier>();
		final var errorHandler = new RequestIdErrorHandler(_ -> {
			seen.set(RequestId.get());
			seenIdentifier.set(Identifier.get());
		});

		errorHandler.handleError(new ListenerExecutionFailedException("failed", new IllegalStateException(), message("request-id")));

		assertThat(seen.get()).isEqualTo("request-id");
		assertThat(seenIdentifier.get()).isEqualTo(Identifier.parse("joe01doe; type=adAccount"));
		assertClearedAfterwards();
	}

	@Test
	void aFailureWithoutMessageGetsANewRequestId() {
		final var seen = new AtomicReference<String>();
		final var errorHandler = new RequestIdErrorHandler(_ -> seen.set(RequestId.get()));

		errorHandler.handleError(new IllegalStateException("not from a listener"));

		assertThat(seen.get()).isNotBlank();
		assertClearedAfterwards();
	}

	@Test
	void passesOnWhatTheDelegateThrows() {
		// As Spring AMQP's ConditionalRejectingErrorHandler does, to reject the message
		final var rejection = new IllegalStateException("reject");
		final var errorHandler = new RequestIdErrorHandler(_ -> {
			throw rejection;
		});
		final var failure = new ListenerExecutionFailedException("failed", new IllegalStateException(), message("request-id"));

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> errorHandler.handleError(failure))
			.isSameAs(rejection);
		assertClearedAfterwards();
	}

	private static Message message(final String requestId) {
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("x-request-id", requestId);
		message.getMessageProperties().setHeader("X-Sent-By", "joe01doe; type=adAccount");
		return message;
	}

	private static void assertClearedAfterwards() {
		assertThat(RequestId.get()).isNull();
		assertThat(Identifier.get()).isNull();
	}
}
