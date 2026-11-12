package se.sundsvall.dept44.rabbitmq.config;

import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static java.nio.charset.StandardCharsets.UTF_8;
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

	@AfterEach
	void forgetFailed() {
		// A failed delivery is kept for the container's error handler, which these tests do not call
		MessageRequestId.forgetFailed();
	}

	@ParameterizedTest
	@MethodSource("requestIdsThatCannotGoOnAsAHeader")
	void aMessageWhoseRequestIdCannotGoOnAsAHeaderGetsANewOne(final Object requestId) throws Throwable {
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("x-request-id", requestId);
		final var seen = whileHandling(message);

		advice.invoke(invocation);

		assertThat(UUID.fromString(seen.get().requestId())).hasToString(seen.get().requestId());
	}

	static Stream<Object> requestIdsThatCannotGoOnAsAHeader() {
		return Stream.of("körning-1", "request\r\nid", "request id", "", "x".repeat(129), "request-id".getBytes(UTF_8));
	}

	@ParameterizedTest
	@MethodSource("requestIdsThatAreKept")
	void aRequestIdThatCanGoOnAsAHeaderIsKept(final String requestId) throws Throwable {
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("x-request-id", requestId);
		final var seen = whileHandling(message);

		advice.invoke(invocation);

		assertThat(seen.get().requestId()).isEqualTo(requestId);
	}

	static Stream<String> requestIdsThatAreKept() {
		// The lowest and the highest character allowed, a UUID as from HTTP, and the longest request id allowed
		return Stream.of("!a/b:c_d.e~", UUID.randomUUID().toString(), "x".repeat(128));
	}

	@Test
	void anIdentifierOutsidePrintableAsciiIsKept() throws Throwable {
		// As over HTTP: the listener's Feign calls fail on it, rather than be made without the user
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("X-Sent-By", "åsa01; type=adAccount");
		final var seen = whileHandling(message);

		advice.invoke(invocation);

		assertThat(seen.get().identifier()).isEqualTo(Identifier.parse("åsa01; type=adAccount"));
	}

	@Test
	void anIdentifierThatIsNotAStringCountsAsNone() throws Throwable {
		// Not a String, though its toString parses
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setHeader("X-Sent-By", new StringBuilder("joe01doe; type=adAccount"));
		final var seen = whileHandling(message);

		advice.invoke(invocation);

		assertThat(seen.get().identifier()).isNull();
	}

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
