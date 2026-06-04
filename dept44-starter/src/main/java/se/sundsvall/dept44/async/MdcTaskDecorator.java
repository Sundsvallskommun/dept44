package se.sundsvall.dept44.async;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import se.sundsvall.dept44.support.Identifier;

import java.util.Map;

/**
 * {@link TaskDecorator} that propagates the logging/correlation context of the submitting (caller) thread to the worker
 * thread that executes an {@code @Async} (or scheduled) task.
 * <p>
 * The full SLF4J {@link MDC} is copied (which covers {@code x-request-id}, {@code sent_by}, {@code sent_by_type},
 * {@code municipalityId} and any future keys such as a tracing {@code traceId}) together with the {@link Identifier}
 * thread-local, so that downstream calls made from the worker thread (Feign/WebClient/WebServiceTemplate) keep
 * propagating the {@code X-Sent-By} identity.
 * <p>
 * The worker thread's previous context is captured and restored after execution so that pooled threads do not leak
 * context between tasks.
 *
 * @see TaskDecorator
 */
public class MdcTaskDecorator implements TaskDecorator {

	@Override
	public @NonNull Runnable decorate(final @NonNull Runnable runnable) {
		// Captured on the caller thread, synchronously when the task is submitted.
		final Map<String, String> callerContextMap = MDC.getCopyOfContextMap();
		final Identifier callerIdentifier = Identifier.get();

		return () -> {
			// Remember whatever the (possibly pooled) worker thread already had, to restore it afterwards.
			final Map<String, String> previousContextMap = MDC.getCopyOfContextMap();
			final Identifier previousIdentifier = Identifier.get();

			applyContext(callerContextMap, callerIdentifier);
			try {
				runnable.run();
			} finally {
				applyContext(previousContextMap, previousIdentifier);
			}
		};
	}

	/**
	 * Applies the given identifier and MDC to the current thread. The MDC is applied last since {@link Identifier#set}
	 * and {@link Identifier#remove} also touch the {@code sent_by}/{@code sent_by_type} MDC keys - letting the explicit
	 * context map win guarantees a consistent MDC state.
	 */
	private static void applyContext(final Map<String, String> contextMap, final Identifier identifier) {
		if (identifier != null) {
			Identifier.set(identifier);
		} else {
			Identifier.remove();
		}

		if (contextMap != null) {
			MDC.setContextMap(contextMap);
		} else {
			MDC.clear();
		}
	}
}
