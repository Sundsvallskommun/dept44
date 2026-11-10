package se.sundsvall.dept44.rabbitmq.config;

import java.util.List;
import java.util.TreeMap;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static java.util.Objects.nonNull;

/**
 * Reports DOWN for each listener container in the registry that should be running but is not, with its queues.
 * <p>
 * Spring AMQP's simple container stops for good when the broker refuses its login, as when the broker is rebuilt and
 * the service's user comes back after the service has tried to reconnect, or when its queue stays missing. Nothing
 * starts it again, and Spring Boot's rabbit health only checks that a connection opens, so the service would report UP
 * while its queue fills. {@link StoppedListenerLiveness} has such a service restarted.
 * <p>
 * Running is not consuming: a container that retries a broker it cannot reach, or a vhost it may not use, is UP. A
 * container that is not started automatically does not count. Until the context has started the containers, and once
 * it closes, the health is UNKNOWN: the registry's own {@code isRunning()} cannot tell, since it turns false only when
 * the last container stops.
 */
class RabbitListenersHealthIndicator implements HealthIndicator, ApplicationListener<ApplicationEvent> {

	private final ApplicationContext context;
	private final RabbitListenerEndpointRegistry registry;
	private volatile boolean started;

	RabbitListenersHealthIndicator(final ApplicationContext context, final RabbitListenerEndpointRegistry registry) {
		this.context = context;
		this.registry = registry;
	}

	@Override
	public Health health() {
		if (!started) {
			return Health.unknown().build();
		}
		final var stopped = new TreeMap<String, List<String>>();
		for (final var id : registry.getListenerContainerIds()) {
			final var container = registry.getListenerContainer(id);
			if (nonNull(container) && container.isAutoStartup() && !container.isRunning()) {
				stopped.put(id, queues(container));
			}
		}
		return stopped.isEmpty() ? Health.up().build() : Health.down().withDetail("stopped", stopped).build();
	}

	/**
	 * The context has started the containers just before it says it has refreshed, and says it closes just before it
	 * stops them.
	 */
	@Override
	public void onApplicationEvent(final ApplicationEvent event) {
		switch (event) {
			case final ContextRefreshedEvent refreshed when refreshed.getApplicationContext() == context -> started = true;
			case final ContextClosedEvent closed when closed.getApplicationContext() == context -> started = false;
			default -> {
				// Nothing about the listener containers
			}
		}
	}

	static List<String> queues(final MessageListenerContainer container) {
		return container instanceof final AbstractMessageListenerContainer listenerContainer ? List.of(listenerContainer.getQueueNames()) : List.of();
	}
}
