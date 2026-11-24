package se.sundsvall.dept44.rabbitmq.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.assertj.core.api.Assertions.assertThat;
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
	void upWhileEveryContainerThatShouldRunRuns() {
		when(running.isAutoStartup()).thenReturn(true);
		when(running.isRunning()).thenReturn(true);
		containers(Map.of("running", running));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void downWithOnlyTheContainersThatShouldRunButDoNot() {
		when(running.isAutoStartup()).thenReturn(true);
		when(running.isRunning()).thenReturn(true);
		// Neither of these is started, and only the first is started automatically
		final var stopped = container("api-fabriken.party-overview.party-assets");
		final var startedByTheService = container("api-fabriken.party-overview.on-demand");
		startedByTheService.setAutoStartup(false);
		containers(Map.of("running", running, "party-assets", stopped, "on-demand", startedByTheService));
		indicator.onApplicationEvent(new ContextRefreshedEvent(context));

		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsExactly(Map.entry("stopped", Map.of("party-assets", List.of("api-fabriken.party-overview.party-assets"))));
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

	private void containers(final Map<String, SimpleMessageListenerContainer> containers) {
		when(registry.getListenerContainerIds()).thenReturn(Set.copyOf(containers.keySet()));
		containers.forEach((id, container) -> when(registry.getListenerContainer(id)).thenReturn(container));
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
