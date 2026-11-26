package se.sundsvall.dept44.rabbitmq.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.ConsumeOkEvent;
import org.springframework.amqp.rabbit.listener.ListenerContainerConsumerFailedEvent;
import org.springframework.amqp.rabbit.listener.MissingQueueEvent;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.support.ConsumerCancelledException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static java.util.Objects.nonNull;

/**
 * Reports DOWN for each listener container in the registry that should be running but is not, under {@code stopped},
 * and for each one that runs without a consumer on one of its queues, under {@code notConsuming}, with the queues.
 * <p>
 * A container runs without a consumer while the broker lacks its queue or refuses its login. It keeps trying, and
 * consumes once the queue or the user is back, while Spring Boot's rabbit health only checks that a connection opens.
 * A queue counts as consumed from once the broker has confirmed a consumer on it, and no longer once the container
 * says the queue is missing or one of its consumers has failed. A cancelled consumer says nothing about the queues: the
 * container has stopped one it no longer needs, while the others go on, or it starts the consumer again, which then
 * says for itself whether its queue is there. A consumer does not say which container it belongs to, so a consumer on
 * a queue counts for every container in the registry that listens to it.
 * <p>
 * A container that is not started automatically does not count. Until the context has started the containers, and
 * once it closes, the health is UNKNOWN: the registry's own {@code isRunning()} cannot tell, since it turns false only
 * when the last container stops.
 */
class RabbitListenersHealthIndicator implements HealthIndicator, ApplicationListener<ApplicationEvent> {

	private final ApplicationContext context;
	private final RabbitListenerEndpointRegistry registry;
	/**
	 * The queues each container has a consumer on. The sets are replaced, never changed, so that an update is atomic.
	 */
	private final Map<MessageListenerContainer, Set<String>> consumed = new ConcurrentHashMap<>();
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
		final var notConsuming = new TreeMap<String, List<String>>();
		for (final var id : registry.getListenerContainerIds()) {
			final var container = registry.getListenerContainer(id);
			if (nonNull(container) && container.isAutoStartup()) {
				if (container.isRunning()) {
					final var withoutConsumer = queuesWithoutConsumer(container);
					if (!withoutConsumer.isEmpty()) {
						notConsuming.put(id, withoutConsumer);
					}
				} else {
					// Started again, it has no consumer until the broker confirms one
					consumed.remove(container);
					stopped.put(id, queues(container));
				}
			}
		}
		if (stopped.isEmpty() && notConsuming.isEmpty()) {
			return Health.up().build();
		}
		final var health = Health.down();
		if (!stopped.isEmpty()) {
			health.withDetail("stopped", stopped);
		}
		if (!notConsuming.isEmpty()) {
			health.withDetail("notConsuming", notConsuming);
		}
		return health.build();
	}

	/**
	 * The context has started the containers just before it says it has refreshed, and says it closes just before it
	 * stops them. The containers' events are recorded either way, since their consumers start before the context has
	 * refreshed.
	 */
	@Override
	public void onApplicationEvent(final ApplicationEvent event) {
		switch (event) {
			case final ContextRefreshedEvent refreshed when refreshed.getApplicationContext() == context -> started = true;
			case final ContextClosedEvent closed when closed.getApplicationContext() == context -> started = false;
			case final ConsumeOkEvent consumeOk -> consumedFrom(consumeOk.getQueue());
			case final MissingQueueEvent missing -> notConsumedFrom(missing.getSource(), missing.getQueue());
			case final ListenerContainerConsumerFailedEvent failed when !(failed.getThrowable() instanceof ConsumerCancelledException) -> consumed.remove(failed.getSource());
			default -> {
				// Nothing about the listener containers
			}
		}
	}

	/**
	 * The event comes from the consumer, which does not say which container it belongs to
	 */
	private void consumedFrom(final String queue) {
		for (final var id : registry.getListenerContainerIds()) {
			final var container = registry.getListenerContainer(id);
			if (nonNull(container) && queues(container).contains(queue)) {
				consumed.merge(container, Set.of(queue), RabbitListenersHealthIndicator::union);
			}
		}
	}

	/**
	 * Only a container in the registry has an entry to change
	 */
	private void notConsumedFrom(final Object container, final String queue) {
		if (container instanceof final MessageListenerContainer listenerContainer) {
			consumed.computeIfPresent(listenerContainer, (_, queues) -> without(queues, queue));
		}
	}

	private List<String> queuesWithoutConsumer(final MessageListenerContainer container) {
		final var consumedFrom = consumed.getOrDefault(container, Set.of());
		final var withoutConsumer = new ArrayList<String>();
		for (final var queue : queues(container)) {
			if (!consumedFrom.contains(queue)) {
				withoutConsumer.add(queue);
			}
		}
		return withoutConsumer;
	}

	private static Set<String> union(final Set<String> queues, final Set<String> more) {
		final var union = new HashSet<>(queues);
		union.addAll(more);
		return Set.copyOf(union);
	}

	private static Set<String> without(final Set<String> queues, final String queue) {
		final var rest = new HashSet<>(queues);
		rest.remove(queue);
		return Set.copyOf(rest);
	}

	static List<String> queues(final MessageListenerContainer container) {
		if (container instanceof final AbstractMessageListenerContainer listenerContainer) {
			return List.of(listenerContainer.getQueueNames());
		}
		return List.of();
	}
}
