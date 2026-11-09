package se.sundsvall.dept44.rabbitmq.config;

import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;

/**
 * Handles a message under the request id and the identifier it was sent with, so that the log lines on both sides
 * of the queue carry the same request id. See {@link MessageRequestId}.
 * <p>
 * Wraps the listener container's call to the listener, on the container's own thread, and clears both afterwards.
 */
class RequestIdListenerAdvice implements MethodInterceptor {

	@Override
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		// The container passes the channel and the message, or a list of them for a batch listener
		final var message = Arrays.stream(invocation.getArguments())
			.filter(Message.class::isInstance)
			.map(Message.class::cast)
			.findFirst()
			.orElse(null);

		MessageRequestId.forgetFailed();
		MessageRequestId.set(message);
		try {
			return invocation.proceed();
		} catch (final RuntimeException e) {
			// For the container's error handler, which logs under what the delivery was handled under as well
			MessageRequestId.keepFailed();
			throw e;
		} finally {
			MessageRequestId.clear();
		}
	}
}
