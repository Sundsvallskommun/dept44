package se.sundsvall.dept44.rabbitmq;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;

import static org.assertj.core.api.Assertions.assertThat;

class TransientFailuresTest {

	static Stream<Throwable> outages() {
		return Stream.of(
			new CannotCreateTransactionException("Could not open JPA EntityManager for transaction", new SQLTransientConnectionException("Connection is not available")),
			new DataAccessResourceFailureException("database down"),
			new QueryTimeoutException("query timed out"),
			new RecoverableDataAccessException("recoverable"),
			new SQLTransientConnectionException("Connection is not available"),
			new SQLRecoverableException("recoverable"),
			new ConnectException("Connection refused"),
			new SocketException("Connection reset"),
			new SocketTimeoutException("Read timed out"),
			new UnknownHostException("rabbitmq.example.se"),
			new AmqpConnectException(new ConnectException("Connection refused")),
			new AmqpTimeoutException(new TimeoutException("The broker did not finish the handshake in time")),
			CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("database")),
			new RabbitPublishException(RabbitPublishException.Reason.TIMEOUT, "message-id", "not confirmed in time"),
			new RabbitPublishException(RabbitPublishException.Reason.INTERRUPTED, "message-id", "interrupted"),
			new RabbitPublishException(RabbitPublishException.Reason.BLOCKED, "message-id", "the broker blocks publishing"));
	}

	static Stream<Throwable> serverErrors() {
		return Stream.of(
			new ServerProblem(HttpStatus.BAD_GATEWAY, "the called service failed"),
			new RabbitPublishException(RabbitPublishException.Reason.NACKED, "message-id", "channel closed"));
	}

	static Stream<Throwable> permanentFailures() {
		return Stream.of(
			new IllegalStateException("a bug"),
			new MessageConversionException("not JSON"),
			new DataIntegrityViolationException("duplicate key"),
			new ClientProblem(HttpStatus.BAD_REQUEST, "the request was invalid"),
			new RabbitPublishException(RabbitPublishException.Reason.RETURNED, "message-id", "no route"));
	}

	static Stream<Throwable> decidedByTheListener() {
		final var outage = new ConnectException("Connection refused");
		return Stream.of(
			new AmqpRejectAndDontRequeueException("given up", outage),
			new ImmediateRequeueAmqpException("put it back", outage),
			new ImmediateAcknowledgeAmqpException("nothing to do"));
	}

	@ParameterizedTest
	@MethodSource("outages")
	void anOutageIsRetriedAndPutBackAlsoWhenWrappedAsTheListenerExceptionReachesTheRetry(final Throwable failure) {
		final var wrapped = listenerFailed(new IllegalStateException("wrapped by the service", failure));

		assertThat(TransientFailures.isTransient(failure)).isTrue();
		assertThat(TransientFailures.isOutage(failure)).isTrue();
		assertThat(TransientFailures.isTransient(wrapped)).isTrue();
		assertThat(TransientFailures.isOutage(wrapped)).isTrue();
	}

	@ParameterizedTest
	@MethodSource("serverErrors")
	void aServerErrorIsRetriedButNotPutBack(final Throwable serverError) {
		final var failure = listenerFailed(serverError);

		assertThat(TransientFailures.isTransient(failure)).isTrue();
		assertThat(TransientFailures.isOutage(failure)).isFalse();
	}

	@ParameterizedTest
	@MethodSource("permanentFailures")
	void permanent(final Throwable failure) {
		assertThat(TransientFailures.isTransient(failure)).isFalse();
		assertThat(TransientFailures.isOutage(failure)).isFalse();
		assertThat(TransientFailures.isTransient(listenerFailed(failure))).isFalse();
	}

	@ParameterizedTest
	@MethodSource("decidedByTheListener")
	void whatTheListenerDecidedIsNeitherRetriedNorPutBackWhateverItWraps(final Throwable decision) {
		final var failure = listenerFailed(decision);

		assertThat(TransientFailures.isTransient(failure)).isFalse();
		assertThat(TransientFailures.isOutage(failure)).isFalse();
	}

	@Test
	void aCauseChainThatLoopsEnds() {
		final var first = new IllegalStateException("first");
		final var second = new IllegalArgumentException("second", first);
		first.initCause(second);

		assertThat(TransientFailures.isTransient(first)).isFalse();
	}

	@Test
	void nothingIsNotTransient() {
		assertThat(TransientFailures.isTransient(null)).isFalse();
		assertThat(TransientFailures.isOutage(null)).isFalse();
	}

	private static ListenerExecutionFailedException listenerFailed(final Throwable cause) {
		return new ListenerExecutionFailedException("Listener method failed", cause, new Message(new byte[0]));
	}
}
