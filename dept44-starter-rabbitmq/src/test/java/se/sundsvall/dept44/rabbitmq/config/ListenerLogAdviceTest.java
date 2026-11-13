package se.sundsvall.dept44.rabbitmq.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({
	MockitoExtension.class, OutputCaptureExtension.class
})
class ListenerLogAdviceTest {

	private final ListenerLogAdvice advice = new ListenerLogAdvice();

	@Mock
	private MethodInvocation invocation;

	@Test
	void aHandledMessageIsAcknowledged(final CapturedOutput output) throws Throwable {
		when(invocation.getArguments()).thenReturn(arguments(message()));
		when(invocation.proceed()).thenReturn(null);

		advice.invoke(invocation);

		assertThat(output.getOut()).containsPattern(line(0, "was acknowledged"));
	}

	@Test
	void aServiceTurnsTheLinesOffThroughTheirLogger(final CapturedOutput output) throws Throwable {
		when(invocation.getArguments()).thenReturn(arguments(message()));
		when(invocation.proceed()).thenReturn(null);
		final var logger = (Logger) LoggerFactory.getLogger("se.sundsvall.dept44.rabbitmq.messages");
		logger.setLevel(Level.WARN);
		try {
			advice.invoke(invocation);
		} finally {
			logger.setLevel(null);
		}

		assertThat(output.getOut()).doesNotContain("Message message-id");
	}

	@Test
	void logsTheDeliveryCountTheBrokerSet(final CapturedOutput output) throws Throwable {
		final var message = message();
		message.getMessageProperties().setHeader("x-delivery-count", 2L);
		when(invocation.getArguments()).thenReturn(arguments(message));
		when(invocation.proceed()).thenReturn(null);

		advice.invoke(invocation);

		assertThat(output.getOut()).containsPattern(line(2, "was acknowledged"));
	}

	@ParameterizedTest
	@MethodSource("failures")
	void aFailedMessageIsLoggedWithWhatBecomesOfIt(final RuntimeException failure, final String outcome, final CapturedOutput output) throws Throwable {
		when(invocation.getArguments()).thenReturn(arguments(message()));
		when(invocation.proceed()).thenThrow(failure);

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> advice.invoke(invocation)).isSameAs(failure);

		assertThat(output.getOut()).containsPattern(line(0, outcome));
	}

	private static Stream<Arguments> failures() {
		final var cause = new IllegalStateException("cause");
		return Stream.of(
			Arguments.of(failed(new ImmediateAcknowledgeAmqpException("nothing to do")), "was acknowledged"),
			Arguments.of(failed(new ImmediateRequeueAmqpException(cause)), "was put back in the queue, which dead-letters it past its x-delivery-limit"),
			Arguments.of(failed(new AmqpRejectAndDontRequeueException(cause)), "was rejected, for the queue to dead-letter"),
			// The first of the two in the cause chain counts, as for the container
			Arguments.of(failed(new ImmediateRequeueAmqpException(new AmqpRejectAndDontRequeueException(cause))), "was put back in the queue, which dead-letters it past its x-delivery-limit"),
			Arguments.of(failed(new AmqpRejectAndDontRequeueException(new ImmediateRequeueAmqpException(cause))), "was rejected, for the queue to dead-letter"),
			// The container looks for an acknowledgement up to a reject or an Error only
			Arguments.of(failed(new AmqpRejectAndDontRequeueException(new ImmediateAcknowledgeAmqpException("nothing to do"))), "was rejected, for the queue to dead-letter"),
			Arguments.of(failed(new IllegalStateException(new Error("fatal", new ImmediateAcknowledgeAmqpException("nothing to do")))), "failed"),
			// Only with the retry turned off, where the container decides
			Arguments.of(cause, "failed"));
	}

	@Test
	void aBatchIsLoggedAsOne(final CapturedOutput output) throws Throwable {
		final var first = message();
		final var second = message();
		second.getMessageProperties().setMessageId("other-message-id");
		when(invocation.getArguments()).thenReturn(new Object[] {
			mock(Channel.class), List.of(first, second)
		});
		when(invocation.proceed()).thenReturn(null);

		advice.invoke(invocation);

		assertThat(output.getOut()).containsPattern(Pattern.quote("Batch of messages message-id, other-message-id from queue 'queue' (") + "\\d+" + Pattern.quote(" ms) was acknowledged"));
	}

	/**
	 * The line, whatever the time it took
	 */
	private static String line(final int deliveryCount, final String outcome) {
		return Pattern.quote("Message message-id from queue 'queue' with routing key 'routing-key' (delivery count " + deliveryCount + ", ") + "\\d+"
			+ Pattern.quote(" ms) " + outcome);
	}

	private static Message message() {
		final var message = new Message(new byte[0]);
		message.getMessageProperties().setMessageId("message-id");
		message.getMessageProperties().setConsumerQueue("queue");
		message.getMessageProperties().setReceivedRoutingKey("routing-key");
		return message;
	}

	private static Object[] arguments(final Message message) {
		return new Object[] {
			mock(Channel.class), message
		};
	}

	/**
	 * As the recoverer throws it
	 */
	private static ListenerExecutionFailedException failed(final Throwable cause) {
		return new ListenerExecutionFailedException("failed", cause, new Message(new byte[0]));
	}
}
