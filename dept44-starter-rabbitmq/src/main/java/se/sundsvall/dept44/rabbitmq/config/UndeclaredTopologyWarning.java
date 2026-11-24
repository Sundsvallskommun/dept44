package se.sundsvall.dept44.rabbitmq.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Warns at startup about queues, exchanges and bindings the service declares as beans, also those a
 * {@code @RabbitListener} declares with {@code bindings} or {@code queuesToDeclare}. Without an {@link AmqpAdmin} bean,
 * such as the {@code RabbitAdmin} that {@code spring.rabbitmq.dynamic=false} leaves out, nothing declares them on the
 * broker. The topology comes from rabbitmq-topology.
 * <p>
 * A service with an {@link AmqpAdmin} bean has chosen to declare them itself, and gets no warning. A lazy bean that has
 * not been created yet is left out rather than created. So is what a lazy listener declares, which Spring AMQP
 * registers only once it creates the listener.
 */
class UndeclaredTopologyWarning implements SmartInitializingSingleton {

	private static final Logger LOG = LoggerFactory.getLogger(UndeclaredTopologyWarning.class);

	private final ConfigurableListableBeanFactory beanFactory;

	UndeclaredTopologyWarning(final ConfigurableListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (beanFactory.getBeanNamesForType(AmqpAdmin.class, true, false).length > 0) {
			return;
		}
		final var undeclared = findUndeclared();
		if (!undeclared.isEmpty()) {
			final var joined = String.join(", ", undeclared);
			LOG.warn("Nothing declares {} on the broker, since the service has no AmqpAdmin bean, as with spring.rabbitmq.dynamic=false. " +
				"Declare them through rabbitmq-topology", joined);
		}
	}

	List<String> findUndeclared() {
		final var declaredOneByOne = created(Declarable.class);
		final var declaredTogether = created(Declarables.class).flatMap(declarables -> declarables.getDeclarables().stream());
		return Stream.concat(declaredOneByOne, declaredTogether)
			// One that a RabbitAdmin would skip as well
			.filter(Declarable::shouldDeclare)
			.map(UndeclaredTopologyWarning::describe)
			.toList();
	}

	/**
	 * The singletons of the type that have been created. Creating a lazy one here would fail the startup for one that
	 * cannot be created, where without the warning it fails only once it is used.
	 */
	private <T> Stream<T> created(final Class<T> type) {
		return Arrays.stream(beanFactory.getBeanNamesForType(type, false, false))
			.filter(beanFactory::containsSingleton)
			.map(name -> beanFactory.getBean(name, type));
	}

	private static String describe(final Declarable declarable) {
		return switch (declarable) {
			case final Queue queue -> "queue '%s'".formatted(queue.getName());
			case final Exchange exchange -> "exchange '%s'".formatted(exchange.getName());
			case final Binding binding -> describe(binding);
			default -> declarable.toString();
		};
	}

	private static String describe(final Binding binding) {
		final var destinationType = binding.getDestinationType().name().toLowerCase(Locale.ROOT);
		return "binding of %s '%s' to exchange '%s' with routing key '%s'"
			.formatted(destinationType, binding.getDestination(), binding.getExchange(), binding.getRoutingKey());
	}
}
