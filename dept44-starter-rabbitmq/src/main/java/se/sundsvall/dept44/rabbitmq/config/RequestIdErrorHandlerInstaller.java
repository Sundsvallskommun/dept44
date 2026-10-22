package se.sundsvall.dept44.rabbitmq.config;

import java.util.Objects;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

/**
 * Wraps the error handler of every listener container in a {@link RequestIdErrorHandler}, keeping the one the container
 * had, Spring AMQP's or the service's own, inside it.
 * <p>
 * Done here rather than on the container factory, which has no getter for its error handler: setting one there would
 * replace the service's. Runs in the phase just before the registry, which starts the containers. A container the
 * service declares as a bean of its own, outside the registry, keeps its error handler as it is.
 */
class RequestIdErrorHandlerInstaller implements SmartLifecycle {

	private final ObjectProvider<RabbitListenerEndpointRegistry> registry;
	private volatile boolean running;

	RequestIdErrorHandlerInstaller(final ObjectProvider<RabbitListenerEndpointRegistry> registry) {
		this.registry = registry;
	}

	@Override
	public void start() {
		registry.getObject().getListenerContainers().stream()
			.filter(AbstractMessageListenerContainer.class::isInstance)
			.map(AbstractMessageListenerContainer.class::cast)
			.filter(container -> Objects.nonNull(container.getErrorHandler()))
			// A restarted context starts this again
			.filter(container -> !(container.getErrorHandler() instanceof RequestIdErrorHandler))
			.forEach(container -> container.setErrorHandler(new RequestIdErrorHandler(container.getErrorHandler())));
		running = true;
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
