package se.sundsvall.dept44.rabbitmq.config;

import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.util.ErrorHandler;

/**
 * Runs a listener container's error handler, the one that logs a failed listener, under the request id of the message
 * or the batch that failed. The container calls it after the listener and its advice chain have returned, when
 * {@link RequestIdListenerAdvice} has already cleared the request id.
 */
class RequestIdErrorHandler implements ErrorHandler {

	private final ErrorHandler delegate;

	RequestIdErrorHandler(final ErrorHandler delegate) {
		this.delegate = delegate;
	}

	ErrorHandler getDelegate() {
		return delegate;
	}

	@Override
	public void handleError(final Throwable throwable) {
		MessageRequestId.setForErrorHandler(throwable, failedMessage(throwable));
		try {
			delegate.handleError(throwable);
		} finally {
			MessageRequestId.clear();
		}
	}

	private static @Nullable Message failedMessage(final Throwable throwable) {
		if (throwable instanceof final ListenerExecutionFailedException failed) {
			return failed.getFailedMessage();
		}
		return null;
	}
}
