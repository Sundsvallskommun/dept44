package se.sundsvall.dept44.rabbitmq;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import se.sundsvall.dept44.exception.ServerProblem;

import static java.util.Objects.requireNonNull;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.BLOCKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.INTERRUPTED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.NACKED;
import static se.sundsvall.dept44.rabbitmq.RabbitPublishException.Reason.TIMEOUT;

/**
 * Tells a failure that may pass on its own from one that fails the same way every time, such as a message that cannot
 * be read or does not validate. A transient failure is one of two kinds:
 * <ul>
 * <li>An outage, which every message meets alike while it lasts: the service's own database, a connection, the
 * broker, an open circuit breaker. A listener retries it, and puts the message back in the queue once the retries are
 * over.</li>
 * <li>A server error, a 5xx from a called service. It may pass, or it may be this message's data that the service
 * fails on every time: dept44's Feign error decoder answers 502 for all of them, so they cannot be told apart. A
 * listener retries it, and has the message dead-lettered once the retries are over, so that a message that always
 * fails holds up no others for longer than that. A publish the broker nacks is handled the same way: a channel that
 * closed passes, an exchange that is missing does not.</li>
 * </ul>
 * Any other failure is dead-lettered at once. A listener that throws {@link AmqpRejectAndDontRequeueException},
 * {@link ImmediateRequeueAmqpException} or {@link ImmediateAcknowledgeAmqpException} has said what becomes of the
 * message, whatever it wraps, so its failure is never transient.
 * <p>
 * The starter's split is {@link #defaults()}. The listener retry and what becomes of a message once the retries are
 * over both follow the {@code TransientFailures} bean, so a service that knows more failures declares its own, built
 * on the defaults:
 *
 * <pre>{@code
 * @Bean
 * TransientFailures transientFailures() {
 * 	return TransientFailures.defaults()
 * 		.withOutage(WebClientRequestException.class::isInstance);
 * }
 * }</pre>
 *
 * Immutable: each {@code with} method returns a new split and leaves the one it was called on as it was.
 */
public final class TransientFailures {

	static final List<Class<? extends Throwable>> OUTAGES = List.of(
		// The database: timeouts, lock and concurrency failures, and a database that cannot be reached
		TransientDataAccessException.class,
		DataAccessResourceFailureException.class,
		RecoverableDataAccessException.class,
		CannotCreateTransactionException.class,
		SQLTransientException.class,
		SQLRecoverableException.class,
		// Anything else that does not answer: a called service, or the broker itself
		SocketException.class,
		SocketTimeoutException.class,
		UnknownHostException.class,
		AmqpConnectException.class,
		// Such as a broker that does not finish the handshake in time
		AmqpTimeoutException.class,
		// A circuit breaker that has opened, on a repository or a Feign client, does not even try the call
		CallNotPermittedException.class);

	private static final TransientFailures DEFAULTS = new TransientFailures(
		Stream.concat(OUTAGES.stream().<Predicate<Throwable>>map(type -> type::isInstance),
			// A publish that timed out or was interrupted: the broker may still have the message. One the broker blocked
			// was not sent, and waits out the alarm. One that was returned does not pass by trying again.
			Stream.of(publish(TIMEOUT), publish(INTERRUPTED), publish(BLOCKED))).toList(),
		List.of(ServerProblem.class::isInstance, publish(NACKED)));

	private final List<Predicate<Throwable>> outages;
	private final List<Predicate<Throwable>> serverErrors;

	private TransientFailures(final List<Predicate<Throwable>> outages, final List<Predicate<Throwable>> serverErrors) {
		this.outages = outages;
		this.serverErrors = serverErrors;
	}

	/**
	 * @return the starter's split, described above
	 */
	public static TransientFailures defaults() {
		return DEFAULTS;
	}

	/**
	 * @param  outage one more outage, tested against each exception in the cause chain
	 * @return        a split that also counts it as an outage
	 */
	public TransientFailures withOutage(final Predicate<Throwable> outage) {
		return new TransientFailures(append(outages, requireNonNull(outage, "outage")), serverErrors);
	}

	/**
	 * @param  serverError one more server error, tested against each exception in the cause chain
	 * @return             a split that also counts it as a server error
	 */
	public TransientFailures withServerError(final Predicate<Throwable> serverError) {
		return new TransientFailures(outages, append(serverErrors, requireNonNull(serverError, "serverError")));
	}

	/**
	 * @return true for an outage or a server error, the failures a listener retries
	 */
	public boolean isTransient(final Throwable throwable) {
		final var causes = causes(throwable);
		return !decidedByTheListener(causes) && (anyMatches(causes, outages) || anyMatches(causes, serverErrors));
	}

	/**
	 * @return true for an outage, the failures a listener puts back in the queue once its retries are over
	 */
	public boolean isOutage(final Throwable throwable) {
		final var causes = causes(throwable);
		return !decidedByTheListener(causes) && anyMatches(causes, outages);
	}

	private static boolean decidedByTheListener(final List<Throwable> causes) {
		return causes.stream().anyMatch(cause -> cause instanceof AmqpRejectAndDontRequeueException
			|| cause instanceof ImmediateRequeueAmqpException
			|| cause instanceof ImmediateAcknowledgeAmqpException);
	}

	private static boolean anyMatches(final List<Throwable> causes, final List<Predicate<Throwable>> kind) {
		return causes.stream().anyMatch(cause -> kind.stream().anyMatch(predicate -> predicate.test(cause)));
	}

	private static Predicate<Throwable> publish(final RabbitPublishException.Reason reason) {
		return cause -> cause instanceof final RabbitPublishException publishException && publishException.getReason() == reason;
	}

	private static List<Predicate<Throwable>> append(final List<Predicate<Throwable>> predicates, final Predicate<Throwable> predicate) {
		return Stream.concat(predicates.stream(), Stream.of(predicate)).toList();
	}

	/**
	 * The whole cause chain counts, since the listener's exception reaches the retry wrapped by Spring AMQP, and often by
	 * the service too.
	 */
	private static List<Throwable> causes(final Throwable throwable) {
		// Also ends a cause chain that loops
		return ExceptionUtils.getThrowableList(throwable);
	}
}
