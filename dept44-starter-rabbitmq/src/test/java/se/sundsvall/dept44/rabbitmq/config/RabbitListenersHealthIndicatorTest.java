package se.sundsvall.dept44.rabbitmq.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.ConsumeOkEvent;
import org.springframework.amqp.rabbit.listener.ListenerContainerConsumerFailedEvent;
import org.springframework.amqp.rabbit.listener.ListenerContainerConsumerTerminatedEvent;
import org.springframework.amqp.rabbit.listener.MissingQueueEvent;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.ConsumerCancelledException;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitListenersHealthIndicatorTest {

	@Mock
	private ApplicationContext context;

	@Mock
	private RabbitListenerEndpointRegistry registry;

	@Mock
	private SimpleMessageListenerContainer running;

	private RabbitListenersHealthIndicator indicator;

	@BeforeEach
	void setUp() {
		indicator = new RabbitListenersHealthIndicator(context, registry);
	}

	@Test
	void unknownUntilTheContextHasStartedTheContainers() {
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
	}

	@Test
	void upWhileEveryContainerThatShouldRunConsumesFromEachOfItsQueues() {
		runningOn("q.a", "q.b");
		containers(Map.of("running", running));
		// The containers start, and their consumers with them, before the context says it has refreshed
		indicator.onApplicationEvent(consumeOk("q.a"));
		indicator.onApplicationEvent(consumeOk("q.b"));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * As when one of a container's queues is missing at startup: the container consumes from the others
	 */
	@Test
	void downForTheQueuesWithoutAConsumerOnly() {
		runningOn("q.a", "q.b");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new MissingQueueEvent(running, "q.b"));
		indicator.onApplicationEvent(consumeOk("q.a"));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("notConsuming", Map.of("running", List.of("q.b"))));
	}

	@Test
	void listsTheContainersByListenerId() {
		final var other = mock(SimpleMessageListenerContainer.class);
		runningOn("q.a");
		when(other.isAutoStartup()).thenReturn(true);
		when(other.isRunning()).thenReturn(true);
		when(other.getQueueNames()).thenReturn(new String[] {
			"q.other"
		});
		// In an order of their own, and with ids a hash map would put in yet another order
		final var containers = new LinkedHashMap<String, SimpleMessageListenerContainer>();
		containers.put("q", running);
		containers.put("b", other);
		containers.put("r", container("q.r"));
		containers.put("c", container("q.c"));
		when(registry.getListenerContainerIds()).thenReturn(new LinkedHashSet<>(containers.keySet()));
		containers.forEach((id, container) -> when(registry.getListenerContainer(id)).thenReturn(container));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var details = indicator.health().getDetails();

		final var notConsuming = (Map<?, ?>) details.get("notConsuming");
		final var stopped = (Map<?, ?>) details.get("stopped");
		assertThat(notConsuming).extractingFromEntries(Map.Entry::getKey).containsExactly("b", "q");
		assertThat(stopped).extractingFromEntries(Map.Entry::getKey).containsExactly("c", "r");
	}

	@Test
	void downWithOnlyTheContainersThatShouldRunButDoNot() {
		runningOn("q.a");
		// Neither of these is started, and only the first is started automatically
		final var stopped = container("api-fabriken.party-overview.party-assets");
		final var startedByTheService = container("api-fabriken.party-overview.on-demand");
		startedByTheService.setAutoStartup(false);
		containers(Map.of("running", running, "party-assets", stopped, "on-demand", startedByTheService));
		indicator.onApplicationEvent(consumeOk("q.a"));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("stopped", Map.of("party-assets", List.of("api-fabriken.party-overview.party-assets"))));
	}

	@Test
	void downWhileARunningContainerHasNoConsumerYet() {
		runningOn("q.a");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("notConsuming", Map.of("running", List.of("q.a"))));
	}

	@Test
	void downForTheQueueTheBrokerSaysIsMissing() {
		runningOn("q.a", "q.b");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(consumeOk("q.a"));
		indicator.onApplicationEvent(consumeOk("q.b"));

		indicator.onApplicationEvent(new MissingQueueEvent(running, "q.b"));

		final var health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("notConsuming", Map.of("running", List.of("q.b"))));

		indicator.onApplicationEvent(consumeOk("q.b"));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void downAfterAConsumerFailureUntilItConsumesAgain() {
		runningOn("q.a");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(consumeOk("q.a"));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

		indicator.onApplicationEvent(new ListenerContainerConsumerFailedEvent(running, "Consumer raised exception, attempting restart", new IOException("Connection reset"), false));

		final var health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("notConsuming", Map.of("running", List.of("q.a"))));

		indicator.onApplicationEvent(consumeOk("q.a"));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * As the simple container says when it stops a consumer it no longer needs, above its concurrency, or when the broker
	 * cancels one. The other consumers keep consuming, and a consumer started again says for itself whether its queue is
	 * there.
	 */
	@Test
	void aCancelledConsumerLeavesTheQueuesAsTheyWere() {
		runningOn("q.a");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(consumeOk("q.a"));

		indicator.onApplicationEvent(new ListenerContainerConsumerFailedEvent(running, "Consumer raised exception, attempting restart", new ConsumerCancelledException(), false));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * The direct container has a consumer for each queue. It says so when the broker cancels one of them, or its channel
	 * closes, and starts only that one again.
	 */
	@Test
	void aCancelledDirectConsumerCountsOnlyForItsOwnQueue() {
		// A simple container stands in for the direct one: the indicator goes by the events, not by the type
		runningOn("q.a", "q.b");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(consumeOk("q.a"));
		indicator.onApplicationEvent(consumeOk("q.b"));

		indicator.onApplicationEvent(new ListenerContainerConsumerTerminatedEvent(running, "Consumer SimpleConsumer [queue=q.b] canceled"));
		indicator.onApplicationEvent(new MissingQueueEvent(running, "q.b"));

		final var health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("notConsuming", Map.of("running", List.of("q.b"))));

		indicator.onApplicationEvent(consumeOk("q.b"));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void aConsumerOnAQueueCountsForEveryContainerInTheRegistryOnIt() {
		runningOn("q.a");
		final var other = mock(SimpleMessageListenerContainer.class);
		when(other.isAutoStartup()).thenReturn(true);
		when(other.isRunning()).thenReturn(true);
		when(other.getQueueNames()).thenReturn(new String[] {
			"q.a"
		});
		containers(Map.of("running", running, "other", other));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		indicator.onApplicationEvent(consumeOk("q.a"));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void downWithTheStoppedAndTheNotConsumingContainersApart() {
		runningOn("q.a");
		final var stopped = container("q.stopped");
		containers(Map.of("running", running, "stopped", stopped));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsOnly(
			Map.entry("stopped", Map.of("stopped", List.of("q.stopped"))),
			Map.entry("notConsuming", Map.of("running", List.of("q.a"))));
	}

	@Test
	void aRunningContainerTheServiceStartsItselfDoesNotCount() {
		when(running.isAutoStartup()).thenReturn(false);
		// Lenient, since the indicator should not even ask: a running container without a consumer would count otherwise
		lenient().when(running.isRunning()).thenReturn(true);
		lenient().when(running.getQueueNames()).thenReturn(new String[] {
			"q.a"
		});
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * As for a service that registers and unregisters listeners at runtime. The same container shows that its entry is
	 * gone, where a service would get a new one when it registers the listener again.
	 */
	@Test
	void forgetsAContainerNoLongerInTheRegistry() {
		runningOn("q.a");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(consumeOk("q.a"));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

		when(registry.getListenerContainerIds()).thenReturn(Set.of());
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
		when(registry.getListenerContainerIds()).thenReturn(Set.of("running"));

		assertThat(indicator.health().getDetails()).containsExactly(Map.entry("notConsuming", Map.of("running", List.of("q.a"))));
	}

	@Test
	void eventsFromAContainerNeitherInTheRegistryNorABeanChangeNothing() {
		runningOn("q.a");
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(consumeOk("q.a"));
		// Such as a container the service creates with new, on the same queue
		final var outside = container("q.a");

		indicator.onApplicationEvent(new ListenerContainerConsumerFailedEvent(outside, "Consumer raised exception, attempting restart", new IOException("Connection reset"), false));
		indicator.onApplicationEvent(new MissingQueueEvent(outside, "q.a"));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * As for a container the service declares as a bean of its own, outside the registry. It keeps Spring AMQP's fatal
	 * defaults, and stops for good once its queue goes missing or the broker refuses its login.
	 */
	@Test
	void downForAStoppedContainerBeanUnderItsBeanName() {
		runningOn("q.a");
		containers(Map.of("running", running));
		when(context.getBeansOfType(MessageListenerContainer.class, false, false)).thenReturn(Map.of("ownContainer", container("q.own")));
		indicator.onApplicationEvent(consumeOk("q.a"));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("stopped", Map.of("ownContainer", List.of("q.own"))));
	}

	@Test
	void aRunningContainerBeanCountsAsAnyOther() {
		runningOn("q.own");
		when(context.getBeansOfType(MessageListenerContainer.class, false, false)).thenReturn(Map.of("ownContainer", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		assertThat(indicator.health().getDetails()).containsExactly(Map.entry("notConsuming", Map.of("ownContainer", List.of("q.own"))));

		indicator.onApplicationEvent(consumeOk("q.own"));

		// Twice, since the bean is not in the registry and must not be forgotten as if unregistered
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

		indicator.onApplicationEvent(new MissingQueueEvent(running, "q.own"));
		assertThat(indicator.health().getDetails()).containsExactly(Map.entry("notConsuming", Map.of("ownContainer", List.of("q.own"))));
	}

	/**
	 * The service starts the container again after the indicator has found it stopped, and the broker confirms its
	 * consumer before the indicator forgets the container's queues
	 */
	@Test
	void keepsAConsumerConfirmedWhileAStoppedContainerIsStartedAgain() {
		when(running.isAutoStartup()).thenReturn(true);
		when(running.getQueueNames()).thenReturn(new String[] {
			"q.a"
		});
		when(running.isRunning()).thenAnswer(_ -> {
			indicator.onApplicationEvent(consumeOk("q.a"));
			return false;
		}).thenReturn(true);
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var foundStopped = indicator.health();
		final var startedAgain = indicator.health();

		assertThat(foundStopped.getDetails()).containsExactly(Map.entry("stopped", Map.of("running", List.of("q.a"))));
		assertThat(startedAgain.getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * The service registers a listener after the indicator has read the registry, and the broker confirms its consumer
	 * before the indicator forgets the containers no longer registered
	 */
	@Test
	void keepsAConsumerOfAContainerRegisteredWhileTheHealthIsChecked() {
		final var registered = new HashMap<String, MessageListenerContainer>();
		when(registry.getListenerContainerIds()).thenAnswer(_ -> Set.copyOf(registered.keySet()));
		when(registry.getListenerContainer(anyString())).thenAnswer(invocation -> registered.get(invocation.getArgument(0, String.class)));
		when(registry.getListenerContainers()).thenAnswer(_ -> List.copyOf(registered.values()));
		final var added = mock(SimpleMessageListenerContainer.class);
		when(added.isAutoStartup()).thenReturn(true);
		when(added.isRunning()).thenReturn(true);
		when(added.getQueueNames()).thenReturn(new String[] {
			"q.added"
		});
		when(running.isAutoStartup()).thenReturn(true);
		when(running.getQueueNames()).thenReturn(new String[] {
			"q.a"
		});
		when(running.isRunning()).thenAnswer(_ -> {
			registered.put("added", added);
			indicator.onApplicationEvent(consumeOk("q.added"));
			return true;
		}).thenReturn(true);
		registered.put("running", running);
		indicator.onApplicationEvent(consumeOk("q.a"));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var whileRegistered = indicator.health();
		final var registeredSince = indicator.health();

		assertThat(whileRegistered.getStatus()).isEqualTo(Status.UP);
		assertThat(registeredSince.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void aContainerStartedAgainIsNotConsumingUntilItsConsumersAreBack() {
		when(running.isAutoStartup()).thenReturn(true);
		// Stopped as it is checked, and still as its queues are forgotten
		when(running.isRunning()).thenReturn(false, false, true);
		when(running.getQueueNames()).thenReturn(new String[] {
			"q.a"
		});
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(consumeOk("q.a"));

		final var whileStopped = indicator.health();
		final var startedAgain = indicator.health();

		assertThat(whileStopped.getStatus()).isEqualTo(Status.DOWN);
		assertThat(whileStopped.getDetails()).containsExactly(Map.entry("stopped", Map.of("running", List.of("q.a"))));
		assertThat(startedAgain.getStatus()).isEqualTo(Status.DOWN);
		assertThat(startedAgain.getDetails()).containsExactly(Map.entry("notConsuming", Map.of("running", List.of("q.a"))));

		indicator.onApplicationEvent(consumeOk("q.a"));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void unknownOnceTheContextCloses() {
		// The registry stops the containers after the context has said it closes
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(new ContextClosedEvent(context));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
	}

	@Test
	void anotherContextSaysNothingAboutTheContainers() {
		// Such as a child context for the management port
		final var other = mock(ApplicationContext.class);
		indicator.onApplicationEvent(new ContextRefreshedEvent(other));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);

		containers(Map.of());
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));
		indicator.onApplicationEvent(new ContextClosedEvent(other));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	private void runningOn(final String... queues) {
		when(running.isAutoStartup()).thenReturn(true);
		when(running.isRunning()).thenReturn(true);
		when(running.getQueueNames()).thenReturn(queues);
	}

	private void containers(final Map<String, SimpleMessageListenerContainer> containers) {
		when(registry.getListenerContainerIds()).thenReturn(Set.copyOf(containers.keySet()));
		containers.forEach((id, container) -> when(registry.getListenerContainer(id)).thenReturn(container));
	}

	/**
	 * The consumer, not the container, says it consumes
	 */
	private static ConsumeOkEvent consumeOk(final String queue) {
		return new ConsumeOkEvent(new Object(), queue, "consumer-tag");
	}

	/**
	 * Not running, and started automatically unless told otherwise
	 */
	private static SimpleMessageListenerContainer container(final String queue) {
		final var container = new SimpleMessageListenerContainer();
		container.setQueueNames(queue);
		return container;
	}
}
