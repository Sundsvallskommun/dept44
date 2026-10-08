package se.sundsvall.dept44.scheduling.config;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

/**
 * Checks at startup that ShedLock accepts the lock settings of every {@link Dept44Scheduled} method, such as a
 * {@code lockAtLeastFor} that is not longer than {@code lockAtMostFor}. ShedLock itself only checks them when the task
 * is triggered, before the scheduler aspect runs: a task with invalid settings would then never run, and never get the
 * health indicator that could show it.
 */
class Dept44ScheduledLockValidator implements SmartInitializingSingleton {

	private final ConfigurableListableBeanFactory beanFactory;
	private final ExtendedLockConfigurationExtractor lockConfigurationExtractor;

	Dept44ScheduledLockValidator(final ConfigurableListableBeanFactory beanFactory, final ExtendedLockConfigurationExtractor lockConfigurationExtractor) {
		this.beanFactory = beanFactory;
		this.lockConfigurationExtractor = lockConfigurationExtractor;
	}

	@Override
	public void afterSingletonsInstantiated() {
		for (final var beanName : beanFactory.getBeanDefinitionNames()) {
			// Only beans that exist: scheduled tasks are singletons, and a lazy bean is not to be created here
			if (beanFactory.containsSingleton(beanName)) {
				validate(beanFactory.getBean(beanName));
			}
		}
	}

	private void validate(final Object bean) {
		final var type = ClassUtils.getUserClass(bean);
		MethodIntrospector.selectMethods(type, (MethodIntrospector.MetadataLookup<Dept44Scheduled>) method -> AnnotatedElementUtils.findMergedAnnotation(method, Dept44Scheduled.class))
			.keySet()
			.forEach(method -> validate(bean, type, method));
	}

	private void validate(final Object bean, final Class<?> type, final Method method) {
		try {
			// Builds the lock configuration exactly as ShedLock does when the task is triggered
			lockConfigurationExtractor.getLockConfiguration(bean, method, new Object[0]);
		} catch (final IllegalArgumentException e) {
			throw new IllegalStateException("Invalid lock settings on scheduled method %s.%s: %s".formatted(type.getSimpleName(), method.getName(), e.getMessage()), e);
		}
	}
}
