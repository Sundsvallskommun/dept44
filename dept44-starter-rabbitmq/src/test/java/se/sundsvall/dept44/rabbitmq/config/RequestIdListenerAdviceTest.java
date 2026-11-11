package se.sundsvall.dept44.rabbitmq.config;

import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@NullUnmarked
class RequestIdListenerAdviceTest {

	private final RequestIdListenerAdvice advice = new RequestIdListenerAdvice();

	@Mock
	private MethodInvocation invocation;

	@Test
	void handlesTheMessageUnderItsRequestIdAndIdentifier() throws Throwable {
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("x-request-id", "request-id");
		message.getMessageProperties().setHeader("X-Sent-By", "joe01doe; type=adAccount");
		final var seen = whileHandling(message);

		advice.invoke(invocation);

		assertThat(seen.get().requestId()).isEqualTo("request-id");
		assertThat(seen.get().identifier()).isEqualTo(Identifier.parse("joe01doe; type=adAccount"));
		assertClearedAfterwards();
	}

	@Test
	void aMessageWithoutRequestIdGetsANewOne() throws Throwable {
		final var seen = whileHandling(new Message(new byte[0]));

		advice.invoke(invocation);

		assertThat(seen.get().requestId()).isNotBlank();
		assertThat(seen.get().identifier()).isNull();
		assertClearedAfterwards();
	}

	@Test
	void aBatchGetsANewRequestId() throws Throwable {
		when(invocation.getArguments()).thenReturn(new Object[] {
			mock(Channel.class), List.of(new Message(new byte[0]))
		});
		final var seen = new AtomicReference<String>();
		when(invocation.proceed()).thenAnswer(_ -> {
			seen.set(RequestId.get());
			return null;
		});

		advice.invoke(invocation);

		assertThat(seen.get()).isNotBlank();
		assertClearedAfterwards();
	}

	@Test
	void clearsAlsoWhenTheListenerFails() throws Throwable {
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("x-request-id", "request-id");
		message.getMessageProperties().setHeader("X-Sent-By", "joe01doe; type=adAccount");
		when(invocation.getArguments()).thenReturn(new Object[] {
			mock(Channel.class), message
		});
		when(invocation.proceed()).thenThrow(new IllegalStateException("listener failed"));

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> advice.invoke(invocation));

		assertClearedAfterwards();
	}

	private AtomicReference<Seen> whileHandling(final Message message) throws Throwable {
		when(invocation.getArguments()).thenReturn(new Object[] {
			mock(Channel.class), message
		});
		final var seen = new AtomicReference<Seen>();
		when(invocation.proceed()).thenAnswer(_ -> {
			seen.set(new Seen(RequestId.get(), Identifier.get()));
			return null;
		});
		return seen;
	}

	private static void assertClearedAfterwards() {
		assertThat(RequestId.get()).isNull();
		assertThat(Identifier.get()).isNull();
	}

	private record Seen(String requestId, Identifier identifier) {
	}
}
