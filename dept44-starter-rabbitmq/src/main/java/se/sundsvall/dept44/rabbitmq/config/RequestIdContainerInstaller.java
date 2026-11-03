package se.sundsvall.dept44.rabbitmq.config;

import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.adapter.AbstractAdaptableMessageListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * Carries the request id into what a listener container does after the listener's own code, in every container in the
 * registry:
 * <ul>
 * <li>Wraps the container's error handler, Spring AMQP's or the service's own, in a {@link RequestIdErrorHandler}.</li>
 * <li>Adds {@link RequestIdHeaders} to the replies a listener sends as its return value. Spring AMQP sends those on the
 * consumer's channel, not through a {@code RabbitTemplate}. It is added after the listener's own reply post-processors,
 * so that a header the service sets itself wins.</li>
 * </ul>
 * Done here rather than on the container factory, which has no getter for either: setting one there would replace the
 * service's. Runs in the phase just before the registry, which starts the containers. A container the service declares
 * as a bean of its own, outside the registry, keeps both as they are.
 */
class RequestIdContainerInstaller implements SmartLifecycle {

	private final ObjectProvider<RabbitListenerEndpointRegistry> registry;
	private volatile boolean running;

	RequestIdContainerInstaller(final ObjectProvider<RabbitListenerEndpointRegistry> registry) {
		this.registry = registry;
	}

	@Override
	public void start() {
		registry.getObject().getListenerContainers().stream()
			.filter(AbstractMessageListenerContainer.class::isInstance)
			.map(AbstractMessageListenerContainer.class::cast)
			.forEach(container -> {
				wrapErrorHandler(container);
				addReplyHeaders(container);
			});
		running = true;
	}

	private static void wrapErrorHandler(final AbstractMessageListenerContainer container) {
		final var errorHandler = container.getErrorHandler();
		// A restarted context starts this again
		if (nonNull(errorHandler) && !(errorHandler instanceof RequestIdErrorHandler)) {
			container.setErrorHandler(new RequestIdErrorHandler(errorHandler));
		}
	}

	private static void addReplyHeaders(final AbstractMessageListenerContainer container) {
		if (container.getMessageListener() instanceof final AbstractAdaptableMessageListener listener) {
			final var postProcessors = new ArrayList<MessagePostProcessor>();
			ofNullable(listener.getBeforeSendReplyPostProcessors()).map(Arrays::asList).ifPresent(postProcessors::addAll);
			// A restarted context starts this again
			if (postProcessors.stream().noneMatch(RequestIdHeaders.class::isInstance)) {
				postProcessors.add(new RequestIdHeaders());
				listener.setBeforeSendReplyPostProcessors(postProcessors.toArray(MessagePostProcessor[]::new));
			}
		}
	}

	@Override
	public void stop() {
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public int getPhase() {
		return registry.getObject().getPhase() - 1;
	}
}
