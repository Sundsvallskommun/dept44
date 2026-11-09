package se.sundsvall.dept44.rabbitmq;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
 * Any other failure is dead-lettered at once. A service that needs another split declares a
 * {@code RabbitListenerRetrySettingsCustomizer} and a {@code MessageRecoverer} of its own, and can build on this one.
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

	static final List<Class<? extends Throwable>> SERVER_ERRORS = List.of(ServerProblem.class);

	private TransientFailures() {}

	/**
	 * @return true for an outage or a server error, the failures a listener retries
	 */
	public static boolean isTransient(final Throwable throwable) {
		final var causes = causes(throwable);
		return !decidedByTheListener(causes) && (isOutage(causes) || causes.stream().anyMatch(cause -> isAny(cause, SERVER_ERRORS) || isPublish(cause, NACKED)));
	}

	/**
	 * @return true for an outage, the failures a listener puts back in the queue once its retries are over
	 */
	public static boolean isOutage(final Throwable throwable) {
		final var causes = causes(throwable);
		return !decidedByTheListener(causes) && isOutage(causes);
	}

	/**
	 * @return true when the listener has said what becomes of the message, see {@link #decidedByTheListener(Set)}
	 */
	public static boolean isDecidedByTheListener(final Throwable throwable) {
		return decidedByTheListener(causes(throwable));
	}

	/**
	 * A publish that timed out or was interrupted: the broker may still have the message. One the broker blocked was not
	 * sent, and waits out the alarm. One that was returned does not pass by trying again.
	 */
	private static boolean isOutage(final Set<Throwable> causes) {
		return causes.stream().anyMatch(cause -> isAny(cause, OUTAGES) || isPublish(cause, TIMEOUT) || isPublish(cause, INTERRUPTED) || isPublish(cause, BLOCKED));
	}

	/**
	 * A listener that throws {@link AmqpRejectAndDontRequeueException}, {@link ImmediateRequeueAmqpException} or
	 * {@link ImmediateAcknowledgeAmqpException} has said what becomes of the message, whatever it wraps.
	 */
	private static boolean decidedByTheListener(final Set<Throwable> causes) {
		return causes.stream().anyMatch(cause -> cause instanceof AmqpRejectAndDontRequeueException
			|| cause instanceof ImmediateRequeueAmqpException
			|| cause instanceof ImmediateAcknowledgeAmqpException);
	}

	private static boolean isPublish(final Throwable cause, final RabbitPublishException.Reason reason) {
		return cause instanceof final RabbitPublishException publishException && publishException.getReason() == reason;
	}

	private static boolean isAny(final Throwable cause, final List<Class<? extends Throwable>> types) {
		return types.stream().anyMatch(type -> type.isInstance(cause));
	}

	/**
	 * The whole cause chain counts, since the listener's exception reaches the retry wrapped by Spring AMQP, and often by
	 * the service too.
	 */
	private static Set<Throwable> causes(final Throwable throwable) {
		// By identity, which also ends a cause chain that loops
		final Set<Throwable> causes = Collections.newSetFromMap(new IdentityHashMap<>());
		var cause = throwable;
		while (cause != null && causes.add(cause)) {
			cause = cause.getCause();
		}
		return causes;
	}
}
