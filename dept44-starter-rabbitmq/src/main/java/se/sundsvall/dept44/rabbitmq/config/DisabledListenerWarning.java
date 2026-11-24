package se.sundsvall.dept44.rabbitmq.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import static org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY;

/**
 * Warns at startup about RabbitMQ listeners that receive nothing, since {@code dept44.rabbitmq.enabled} is off.
 * <p>
 * A warning and not a failure: a service may keep a listener in place with RabbitMQ off, waiting to be turned on.
 * Without it, a listener that was meant to run would stop receiving without a trace while its queue grows.
 */
class DisabledListenerWarning implements SmartInitializingSingleton {

	private static final Logger LOG = LoggerFactory.getLogger(DisabledListenerWarning.class);

	private final ConfigurableListableBeanFactory beanFactory;

	DisabledListenerWarning(final ConfigurableListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public void afterSingletonsInstantiated() {
		final var listeners = findListeners();
		if (!listeners.isEmpty()) {
			final var listenersAsString = String.join(", ", listeners);
			LOG.warn("RabbitMQ is off (dept44.rabbitmq.enabled is not true), so the listeners in {} receive nothing", listenersAsString);
		}
	}

	List<String> findListeners() {
		return Arrays.stream(beanFactory.getBeanDefinitionNames())
			.map(this::listenerClass)
			.flatMap(Optional::stream)
			.distinct()
			.toList();
	}

	/**
	 * The name of the bean's class, when it has a listener. Skips a bean whose type cannot be looked through, as Spring's
	 * {@code EventListenerMethodProcessor} does: a lazy bean whose class has a method signature with a class that is not
	 * there at runtime fails once it is created, which it may never be, and a warning must not fail the startup instead.
	 */
	private Optional<String> listenerClass(final String beanName) {
		try {
			return Optional.ofNullable(beanFactory.getType(beanName, false))
				.map(ClassUtils::getUserClass)
				// Skips the types that cannot carry the annotation, such as java.*, before their methods are looked through
				.filter(type -> AnnotationUtils.isCandidateClass(type, RabbitListener.class))
				.filter(DisabledListenerWarning::hasListener)
				.map(Class::getName);
		} catch (final RuntimeException | LinkageError e) {
			LOG.debug("Could not look through bean '{}' for RabbitMQ listeners", beanName, e);
			return Optional.empty();
		}
	}

	private static boolean hasListener(final Class<?> type) {
		final var isListenerClass = MergedAnnotations.from(type, TYPE_HIERARCHY).isPresent(RabbitListener.class);
		if (isListenerClass) {
			return true;
		}
		final ReflectionUtils.MethodFilter isListenerMethod = method -> MergedAnnotations.from(method, TYPE_HIERARCHY).isPresent(RabbitListener.class);
		final var listenerMethods = MethodIntrospector.selectMethods(type, isListenerMethod);
		return !listenerMethods.isEmpty();
	}
}
