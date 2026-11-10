package se.sundsvall.dept44.rabbitmq.config;

import java.io.EOFException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.util.List;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ContainerUtils;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.http.HttpStatus;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.rabbitmq.TransientFailures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransientFailureRecovererTest {

	private final TransientFailureRecoverer recoverer = new TransientFailureRecoverer(TransientFailures.defaults());
	private final Message message = new Message(new byte[0]);

	@Test
	void aFailureTheServiceCountsAsAnOutageGoesBackInTheQueue() {
		final var serviceRecoverer = new TransientFailureRecoverer(TransientFailures.defaults().withOutage(EOFException.class::isInstance));
		final var failure = listenerFailed(new UncheckedIOException(new EOFException("unexpected end of stream")));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> serviceRecoverer.recover(message, failure))
			.withCauseInstanceOf(ImmediateRequeueAmqpException.class);
	}

	@Test
	void anOutageGoesBackInTheQueue() {
		final var failure = listenerFailed(new ConnectException("Connection refused"));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(message, failure))
			.withCauseInstanceOf(ImmediateRequeueAmqpException.class)
			.satisfies(this::isForTheMessage);
	}

	@Test
	void aServerErrorThatOutlastedItsRetriesIsRejected() {
		final var failure = listenerFailed(new ServerProblem(HttpStatus.BAD_GATEWAY, "the called service failed"));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(message, failure))
			.withCauseInstanceOf(AmqpRejectAndDontRequeueException.class)
			.satisfies(this::isForTheMessage);
	}

	@Test
	void anyOtherFailureIsRejected() {
		final var failure = listenerFailed(new IllegalStateException("a bug"));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(message, failure))
			.withCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
	}

	@Test
	void anInterruptedListenerPutsTheMessageBackWhateverTheFailure() {
		final var failure = listenerFailed(new ServerProblem(HttpStatus.BAD_GATEWAY, "the called service failed"));

		Thread.currentThread().interrupt();
		try {
			assertThatExceptionOfType(ListenerExecutionFailedException.class)
				.isThrownBy(() -> recoverer.recover(message, failure))
				.withCauseInstanceOf(ImmediateRequeueAmqpException.class);
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void aListenerThatWasInterruptedButClearedTheFlagPutsTheMessageBack() {
		// As a listener that catches the interrupt and throws it on wrapped, without interrupting itself again
		final var failure = listenerFailed(new IllegalStateException("interrupted", new InterruptedException()));

		assertThat(Thread.currentThread().isInterrupted()).isFalse();
		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(message, failure))
			.withCauseInstanceOf(ImmediateRequeueAmqpException.class);
	}

	@Test
	void aListenerThatAsksForTheMessageBackGetsIt() {
		final var failure = listenerFailed(new ImmediateRequeueAmqpException("put it back"));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(message, failure))
			.satisfies(exception -> assertThat(ContainerUtils.shouldRequeue(false, exception, LogFactory.getLog(TransientFailureRecovererTest.class))).isTrue());
	}

	@Test
	void aListenerThatAsksForTheMessageToBeAcknowledgedGetsIt() {
		final var failure = listenerFailed(new ImmediateAcknowledgeAmqpException("nothing to do"));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(message, failure))
			.satisfies(exception -> assertThat(ContainerUtils.isImmediateAcknowledge(exception)).isTrue());
	}

	@Test
	void aListenerThatRejectsTheMessageGetsItAlsoWhenInterrupted() {
		// As a listener that catches the interrupt and gives up on the message
		final var failure = listenerFailed(new AmqpRejectAndDontRequeueException("give up", new InterruptedException()));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(message, failure))
			.satisfies(exception -> assertThat(ContainerUtils.shouldRequeue(true, exception, LogFactory.getLog(TransientFailureRecovererTest.class))).isFalse());
	}

	@Test
	void aListenerThatAsksForTheMessageToBeAcknowledgedGetsItAlsoWhenInterrupted() {
		final var failure = listenerFailed(new ImmediateAcknowledgeAmqpException("nothing to do"));

		Thread.currentThread().interrupt();
		try {
			assertThatExceptionOfType(ListenerExecutionFailedException.class)
				.isThrownBy(() -> recoverer.recover(message, failure))
				.satisfies(exception -> {
					assertThat(ContainerUtils.isImmediateAcknowledge(exception)).isTrue();
					assertThat(ContainerUtils.shouldRequeue(false, exception, LogFactory.getLog(TransientFailureRecovererTest.class))).isFalse();
				});
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void aBatchIsHandledAsOne() {
		final var other = new Message(new byte[0]);
		final var failure = listenerFailed(new IllegalStateException("a bug"));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(List.of(message, other), failure))
			.withCauseInstanceOf(AmqpRejectAndDontRequeueException.class)
			.satisfies(exception -> assertThat(exception.getFailedMessages()).containsExactly(message, other));
	}

	@Test
	void aBatchInAnOutageIsRejected() {
		// Spring AMQP puts a batch back with a basic.nack, which the queue's delivery limit does not count
		final var other = new Message(new byte[0]);
		final var failure = listenerFailed(new ConnectException("Connection refused"));

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
			.isThrownBy(() -> recoverer.recover(List.of(message, other), failure))
			.withCauseInstanceOf(AmqpRejectAndDontRequeueException.class)
			.satisfies(exception -> assertThat(exception.getFailedMessages()).containsExactly(message, other));
	}

	private void isForTheMessage(final ListenerExecutionFailedException exception) {
		// The container's error handler logs under the request id of the message it finds here
		assertThat(exception.getFailedMessage()).isSameAs(message);
	}

	private ListenerExecutionFailedException listenerFailed(final Throwable cause) {
		return new ListenerExecutionFailedException("Listener method failed", cause, message);
	}
}
