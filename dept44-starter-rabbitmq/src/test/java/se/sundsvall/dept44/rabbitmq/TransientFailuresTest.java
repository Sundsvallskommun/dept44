package se.sundsvall.dept44.rabbitmq;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.EOFException;
import java.io.UncheckedIOException;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransientFailuresTest {

	private static final TransientFailures DEFAULTS = TransientFailures.defaults();

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

		assertThat(DEFAULTS.isTransient(failure)).isTrue();
		assertThat(DEFAULTS.isOutage(failure)).isTrue();
		assertThat(DEFAULTS.isTransient(wrapped)).isTrue();
		assertThat(DEFAULTS.isOutage(wrapped)).isTrue();
	}

	@ParameterizedTest
	@MethodSource("serverErrors")
	void aServerErrorIsRetriedButNotPutBack(final Throwable serverError) {
		final var failure = listenerFailed(serverError);

		assertThat(DEFAULTS.isTransient(failure)).isTrue();
		assertThat(DEFAULTS.isOutage(failure)).isFalse();
	}

	@ParameterizedTest
	@MethodSource("permanentFailures")
	void permanent(final Throwable failure) {
		assertThat(DEFAULTS.isTransient(failure)).isFalse();
		assertThat(DEFAULTS.isOutage(failure)).isFalse();
		assertThat(DEFAULTS.isTransient(listenerFailed(failure))).isFalse();
	}

	@ParameterizedTest
	@MethodSource("decidedByTheListener")
	void whatTheListenerDecidedIsNeitherRetriedNorPutBackWhateverItWraps(final Throwable decision) {
		final var failure = listenerFailed(decision);

		assertThat(DEFAULTS.isTransient(failure)).isFalse();
		assertThat(DEFAULTS.isOutage(failure)).isFalse();
	}

	@Test
	void aServiceAddsAnOutage() {
		// As the end of stream OkHttp reports when the called service closes the connection mid-request
		final var failure = listenerFailed(new UncheckedIOException(new EOFException("unexpected end of stream")));

		final var transientFailures = DEFAULTS.withOutage(EOFException.class::isInstance);

		assertThat(transientFailures.isTransient(failure)).isTrue();
		assertThat(transientFailures.isOutage(failure)).isTrue();
		// The split it was built on is as it was
		assertThat(DEFAULTS.isTransient(failure)).isFalse();
	}

	@Test
	void aServiceAddsAServerError() {
		final var failure = listenerFailed(new ClientProblem(HttpStatus.TOO_MANY_REQUESTS, "slow down"));

		final var transientFailures = DEFAULTS.withServerError(e -> e instanceof final ClientProblem problem && problem.getStatus() == HttpStatus.TOO_MANY_REQUESTS);

		assertThat(transientFailures.isTransient(failure)).isTrue();
		assertThat(transientFailures.isOutage(failure)).isFalse();
		assertThat(DEFAULTS.isTransient(failure)).isFalse();
	}

	@ParameterizedTest
	@MethodSource("decidedByTheListener")
	void whatTheListenerDecidedIsNeitherRetriedNorPutBackWhateverTheServiceAdded(final Throwable decision) {
		final var everything = DEFAULTS.withOutage(_ -> true).withServerError(_ -> true);

		assertThat(everything.isTransient(listenerFailed(decision))).isFalse();
		assertThat(everything.isOutage(listenerFailed(decision))).isFalse();
	}

	@Test
	void anAdditionMustBeGiven() {
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> DEFAULTS.withOutage(null)).withMessage("outage");
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> DEFAULTS.withServerError(null)).withMessage("serverError");
	}

	@Test
	void aCauseChainThatLoopsEnds() {
		final var first = new IllegalStateException("first");
		final var second = new IllegalArgumentException("second", first);
		first.initCause(second);

		assertThat(DEFAULTS.isTransient(first)).isFalse();
	}

	@Test
	void nothingIsNotTransient() {
		assertThat(DEFAULTS.isTransient(null)).isFalse();
		assertThat(DEFAULTS.isOutage(null)).isFalse();
	}

	private static ListenerExecutionFailedException listenerFailed(final Throwable cause) {
		return new ListenerExecutionFailedException("Listener method failed", cause, new Message(new byte[0]));
	}
}
