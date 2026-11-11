package se.sundsvall.dept44.rabbitmq.config;

import com.rabbitmq.client.PossibleAuthenticationFailureException;
import java.util.Optional;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.listener.FatalListenerStartupException;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.ListenerContainerConsumerFailedEvent;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Marks the application {@link LivenessState#BROKEN} once Spring AMQP has stopped a listener container for good
 * because the broker lacks the service's user or queue. Its liveness probe then fails, and Kubernetes restarts it. The
 * restart starts the container again, or fails until the user or the queue is back.
 * <p>
 * Spring AMQP's simple container stops for good, and says so with a fatal {@link ListenerContainerConsumerFailedEvent}
 * once it has stopped, when the broker refuses its login ({@link FatalListenerStartupException},
 * {@link PossibleAuthenticationFailureException}) or its queue stays missing (a {@code QueuesNotAvailableException},
 * which is a {@code FatalListenerStartupException}). Nothing starts it again.
 * <p>
 * Any other fatal stop leaves the application as it is: a consumer interrupted by a stop the service makes itself, or a
 * message the listener cannot handle, which the restarted service would only get again. So does every stop as the
 * context closes. A container the service declares as a bean of its own counts too, though the health of
 * {@link RabbitListenersHealthIndicator} only shows those in the registry.
 */
class StoppedListenerLiveness implements ApplicationListener<ApplicationEvent> {

	private static final Logger LOG = LoggerFactory.getLogger(StoppedListenerLiveness.class);

	private final ApplicationContext context;
	private volatile boolean started;

	StoppedListenerLiveness(final ApplicationContext context) {
		this.context = context;
	}

	@Override
	public void onApplicationEvent(final ApplicationEvent event) {
		switch (event) {
			case final ContextRefreshedEvent refreshed when refreshed.getApplicationContext() == context -> started = true;
			case final ContextClosedEvent closed when closed.getApplicationContext() == context -> started = false;
			case final ListenerContainerConsumerFailedEvent failed when started && failed.isFatal() && brokerLacksSomething(failed.getThrowable())
				&& failed.getSource() instanceof final MessageListenerContainer container && !container.isRunning() -> markBroken(container, failed);
			default -> {
				// Nothing about a container stopped for good
			}
		}
	}

	private static boolean brokerLacksSomething(final @Nullable Throwable failure) {
		return ExceptionUtils.indexOfType(failure, FatalListenerStartupException.class) != -1
			|| ExceptionUtils.indexOfType(failure, PossibleAuthenticationFailureException.class) != -1;
	}

	private void markBroken(final MessageListenerContainer container, final ListenerContainerConsumerFailedEvent failed) {
		final var failure = Optional.ofNullable(failed.getThrowable()).map(Throwable::toString).orElse(null);
		LOG.error("RabbitMQ listener {} on queues {} has stopped for good: {}. Marking the application broken, so that its liveness probe fails and it is restarted",
			container instanceof final AbstractMessageListenerContainer listenerContainer ? listenerContainer.getListenerId() : container,
			RabbitListenersHealthIndicator.queues(container), failure);
		AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
	}
}
