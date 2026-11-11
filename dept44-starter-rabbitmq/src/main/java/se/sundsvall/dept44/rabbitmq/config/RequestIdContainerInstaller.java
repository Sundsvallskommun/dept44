package se.sundsvall.dept44.rabbitmq.config;

import java.util.ArrayList;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.adapter.AbstractAdaptableMessageListener;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareBatchMessageListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

import static java.util.Optional.ofNullable;

/**
 * Carries the request id into what a listener container does after the listener's own code, in every container in the
 * registry when the context starts:
 * <ul>
 * <li>Wraps the container's error handler, Spring AMQP's or the service's own, in a {@link RequestIdErrorHandler}.</li>
 * <li>Adds {@link RequestIdHeaders} to the replies a listener sends as its return value. Spring AMQP sends those on the
 * consumer's channel, not through a {@code RabbitTemplate}. It is added after the listener's own reply post-processors,
 * so that a header the service sets itself wins.</li>
 * </ul>
 * It also warns about a listener whose messages never reach the dead letter queue after an outage, see
 * {@link #warnIfPutBackUncounted}, and fails the startup for a channel-transacted container while the publisher
 * confirms are on, see {@link RabbitMqConfiguration#requireNoTransactionWithConfirms}.
 * <p>
 * Done here rather than on the container factory, which has no getter for either: setting one there would replace the
 * service's. Runs in the phase just before the registry, which starts the containers. A container the service declares
 * as a bean of its own, outside the registry, keeps both as they are, and so does one created after startup.
 */
class RequestIdContainerInstaller implements SmartLifecycle {

	private static final Logger LOG = LoggerFactory.getLogger(RequestIdContainerInstaller.class);

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
				RabbitMqConfiguration.requireNoTransactionWithConfirms(container.isChannelTransacted(), container.getConnectionFactory(),
					"Listener %s on queues %s".formatted(container.getListenerId(), Arrays.toString(container.getQueueNames())));
				wrapErrorHandler(container);
				addReplyHeaders(container);
				warnIfPutBackUncounted(container);
			});
		running = true;
	}

	private static void wrapErrorHandler(final AbstractMessageListenerContainer container) {
		final var errorHandler = container.getErrorHandler();
		// A restarted context starts this again
		if (!(errorHandler instanceof RequestIdErrorHandler)) {
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

	/**
	 * Where the listener may settle a message itself, Spring AMQP puts a failed one back with a {@code basic.nack}, which
	 * RabbitMQ does not count against {@code x-delivery-limit}. Left out are a batch listener, whose batch
	 * {@link TransientFailureRecoverer} dead-letters rather than puts back, and an async listener, which Spring AMQP has
	 * switched to manual acknowledgement by now and whose failed message it rejects.
	 */
	private static void warnIfPutBackUncounted(final AbstractMessageListenerContainer container) {
		if (container.getAcknowledgeMode() == AcknowledgeMode.AUTO
			&& container.isExposeListenerChannel()
			&& container.getMessageListener() instanceof final AbstractAdaptableMessageListener listener
			&& !(listener instanceof ChannelAwareBatchMessageListener)
			&& listener.maySettleDelivery()) {
			final var queueNames = Arrays.toString(container.getQueueNames());
			LOG.warn(
				"Listener {} on queues {} may settle a message itself, through a Channel parameter or an errorHandler. Spring AMQP then puts a failed message back with a basic.nack, which RabbitMQ does not count against x-delivery-limit, so a message put back after an outage never reaches the dead letter queue",
				container.getListenerId(), queueNames);
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
