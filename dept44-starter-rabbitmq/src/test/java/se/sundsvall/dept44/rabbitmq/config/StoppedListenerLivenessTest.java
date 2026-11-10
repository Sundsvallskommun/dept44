package se.sundsvall.dept44.rabbitmq.config;

import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.PossibleAuthenticationFailureException;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpAuthenticationException;
import org.springframework.amqp.listener.FatalListenerExecutionException;
import org.springframework.amqp.listener.FatalListenerStartupException;
import org.springframework.amqp.rabbit.listener.ListenerContainerConsumerFailedEvent;
import org.springframework.amqp.rabbit.listener.QueuesNotAvailableException;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoppedListenerLivenessTest {

	@Mock
	private ApplicationContext context;

	@Mock
	private SimpleMessageListenerContainer container;

	private StoppedListenerLiveness liveness;

	@BeforeEach
	void setUp() {
		liveness = new StoppedListenerLiveness(context);
		// Spring AMQP publishes a fatal failure once it has stopped the container
		lenient().when(container.isRunning()).thenReturn(false);
		lenient().when(container.getQueueNames()).thenReturn(new String[] {
			"api-fabriken.party-overview.party-assets"
		});
	}

	/**
	 * What the simple container publishes as it stops for good because the broker lacks the service's user or queue
	 */
	static Stream<Throwable> brokerLacksSomething() {
		return Stream.of(
			// A refused login as the consumer starts again
			new FatalListenerStartupException("Authentication failure", new AmqpAuthenticationException(new AuthenticationFailureException("ACCESS_REFUSED"))),
			new PossibleAuthenticationFailureException("ACCESS_REFUSED"),
			// A queue still missing after the declaration retries
			new QueuesNotAvailableException("Cannot prepare queue for listener", new IOException("NOT_FOUND")));
	}

	@ParameterizedTest
	@MethodSource("brokerLacksSomething")
	void aContainerStoppedForGoodBecauseTheBrokerLacksSomethingMarksTheApplicationBroken(final Throwable failure) {
		liveness.onApplicationEvent(new ContextRefreshedEvent(context));

		liveness.onApplicationEvent(fatal(failure));

		verify(context).publishEvent(argThat((final ApplicationEvent event) -> event instanceof final AvailabilityChangeEvent<?> change && change.getState() == LivenessState.BROKEN));
	}

	static Stream<Throwable> notTheBroker() {
		return Stream.of(
			// The container's own stop interrupted the consumer, as when the service stops it during a retry
			new InterruptedException(),
			// A message the listener cannot handle, which a restart would only get again
			new FatalListenerExecutionException("Fatal message conversion error", new IllegalStateException("not JSON")));
	}

	@ParameterizedTest
	@MethodSource("notTheBroker")
	void aContainerStoppedForAnyOtherReasonLeavesTheApplicationAsItIs(final Throwable failure) {
		liveness.onApplicationEvent(new ContextRefreshedEvent(context));

		liveness.onApplicationEvent(fatal(failure));

		verifyNotMarked();
	}

	@Test
	void aFailureTheContainerRecoversFromLeavesTheApplicationAsItIs() {
		liveness.onApplicationEvent(new ContextRefreshedEvent(context));

		liveness.onApplicationEvent(new ListenerContainerConsumerFailedEvent(container, "Consumer raised exception, attempting restart", new PossibleAuthenticationFailureException("ACCESS_REFUSED"), false));

		verifyNotMarked();
	}

	@Test
	void aContainerThatStillRunsLeavesTheApplicationAsItIs() {
		// Defensive: the simple container publishes a fatal failure only once it has stopped
		lenient().when(container.isRunning()).thenReturn(true);
		liveness.onApplicationEvent(new ContextRefreshedEvent(context));

		liveness.onApplicationEvent(fatal(new PossibleAuthenticationFailureException("ACCESS_REFUSED")));

		verifyNotMarked();
	}

	@Test
	void containersThatStopAsTheContextClosesLeaveTheApplicationAsItIs() {
		liveness.onApplicationEvent(new ContextRefreshedEvent(context));
		liveness.onApplicationEvent(new ContextClosedEvent(context));

		liveness.onApplicationEvent(fatal(new PossibleAuthenticationFailureException("ACCESS_REFUSED")));

		verifyNotMarked();
	}

	@Test
	void anotherContextSaysNothingAboutTheContainers() {
		// Such as a child context for the management port
		final var other = mock(ApplicationContext.class);
		liveness.onApplicationEvent(new ContextRefreshedEvent(other));
		liveness.onApplicationEvent(fatal(new PossibleAuthenticationFailureException("ACCESS_REFUSED")));
		verifyNotMarked();

		liveness.onApplicationEvent(new ContextRefreshedEvent(context));
		liveness.onApplicationEvent(new ContextClosedEvent(other));
		liveness.onApplicationEvent(fatal(new PossibleAuthenticationFailureException("ACCESS_REFUSED")));
		verify(context).publishEvent(any(ApplicationEvent.class));
	}

	private ListenerContainerConsumerFailedEvent fatal(final Throwable failure) {
		return new ListenerContainerConsumerFailedEvent(container, "Consumer failed", failure, true);
	}

	private void verifyNotMarked() {
		verify(context, never()).publishEvent(any(ApplicationEvent.class));
	}
}
