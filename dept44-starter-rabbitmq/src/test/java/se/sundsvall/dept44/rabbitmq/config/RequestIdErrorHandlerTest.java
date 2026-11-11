package se.sundsvall.dept44.rabbitmq.config;

import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@NullUnmarked
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
	void aFailedMessageWithoutRequestIdIsHandledUnderTheOneItWasHandledUnder() throws Throwable {
		final var withoutRequestId = new Message(new byte[0]);
		final var handledUnder = new AtomicReference<String>();
		final var seen = new AtomicReference<String>();
		final var errorHandler = new RequestIdErrorHandler(_ -> seen.set(RequestId.get()));

		// As the container does: the advice chain throws, and then the container calls its error handler
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> new RequestIdListenerAdvice().invoke(failing(withoutRequestId, handledUnder)));
		errorHandler.handleError(new ListenerExecutionFailedException("failed", new IllegalStateException(), withoutRequestId));

		assertThat(seen.get()).isNotBlank().isEqualTo(handledUnder.get());
		assertClearedAfterwards();
	}

	@Test
	void aFailedMessageIsHandledUnderItsRequestIdAndIdentifierAfterTheAdvice() throws Throwable {
		final var message = message("request-id");
		final var seen = new AtomicReference<String>();
		final var seenIdentifier = new AtomicReference<Identifier>();
		final var errorHandler = new RequestIdErrorHandler(_ -> {
			seen.set(RequestId.get());
			seenIdentifier.set(Identifier.get());
		});

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> new RequestIdListenerAdvice().invoke(failing(message, new AtomicReference<>())));
		errorHandler.handleError(new ListenerExecutionFailedException("failed", new IllegalStateException(), message));

		assertThat(seen.get()).isEqualTo("request-id");
		assertThat(seenIdentifier.get()).isEqualTo(Identifier.parse("joe01doe; type=adAccount"));
		assertClearedAfterwards();
	}

	@Test
	void aFailedBatchIsHandledUnderTheRequestIdTheBatchWasHandledUnder() throws Throwable {
		final var batch = List.of(message("first-request-id"), message("second-request-id"));
		final var handledUnder = new AtomicReference<String>();
		final var seen = new AtomicReference<String>();
		final var errorHandler = new RequestIdErrorHandler(_ -> seen.set(RequestId.get()));

		// As the container does: the advice chain throws, and then the container calls its error handler
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> new RequestIdListenerAdvice().invoke(failing(batch, handledUnder)));
		errorHandler.handleError(new ListenerExecutionFailedException("failed", new IllegalStateException(), batch.toArray(Message[]::new)));

		assertThat(seen.get()).isNotBlank().isEqualTo(handledUnder.get());
		assertClearedAfterwards();
	}

	@Test
	void theRequestIdOfAFailedBatchIsNotKeptPastTheNextDelivery() throws Throwable {
		final var advice = new RequestIdListenerAdvice();
		final var seen = new AtomicReference<String>();
		final var errorHandler = new RequestIdErrorHandler(_ -> seen.set(RequestId.get()));
		// A batch fails in a container whose error handler is not this one
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> advice.invoke(failing(List.of(message("batch-request-id")), new AtomicReference<>())));

		final var next = message("request-id");
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> advice.invoke(failing(next, new AtomicReference<>())));
		errorHandler.handleError(new ListenerExecutionFailedException("failed", new IllegalStateException(), next));

		assertThat(seen.get()).isEqualTo("request-id");
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

	/**
	 * The container's call to a listener that fails, with a message or a batch, and records the request id it ran under
	 */
	private static MethodInvocation failing(final Object data, final AtomicReference<String> handledUnder) throws Throwable {
		final var invocation = mock(MethodInvocation.class);
		when(invocation.getArguments()).thenReturn(new Object[] {
			mock(Channel.class), data
		});
		when(invocation.proceed()).thenAnswer(_ -> {
			handledUnder.set(RequestId.get());
			throw new IllegalStateException("listener failed");
		});
		return invocation;
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
