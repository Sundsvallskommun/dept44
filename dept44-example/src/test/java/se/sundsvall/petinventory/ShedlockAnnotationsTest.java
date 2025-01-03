package se.sundsvall.petinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.zalando.fauxpas.FauxPas.throwingFunction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

class ShedlockAnnotationsTest {

	@Test
	void verifyMandatorySchedlockAnnotations() {
		final var scanner = new ClassPathScanningCandidateComponentProvider(true);
		final var candidates = scanner.findCandidateComponents(this.getClass().getPackageName());
		candidates.addAll(scanner.findCandidateComponents("se.sundsvall.dept44.scheduling"));
		final var hasEnableSchedulerLock = hasEnableSchedulerLock(candidates);

		candidates.stream()
			.map(this::getMethodsAnnotatedWith)
			.flatMap(m -> m.entrySet().stream())
			.forEach(set -> this.verifyAnnotations(hasEnableSchedulerLock, set));
	}

	private boolean hasEnableSchedulerLock(final Set<BeanDefinition> candidates) {
		return candidates.stream()
			.map(BeanDefinition::getBeanClassName)
			.map(throwingFunction(Class::forName))
			.anyMatch(c -> c.isAnnotationPresent(EnableSchedulerLock.class));
	}

	private void verifyAnnotations(final boolean hasEnableSchedulerLockAnnotation, final Entry<String, List<Method>> entrySet) {
		entrySet.getValue().forEach(method -> {
			assertThat(method.isAnnotationPresent(SchedulerLock.class) || method.isAnnotationPresent(Dept44Scheduled.class))
				.withFailMessage(() -> "Method %s in class %s has @Dept44Scheduled annotation but no @SchedulerLock annotation".formatted(method.getName(), entrySet.getKey()))
				.isTrue();

			assertThat(hasEnableSchedulerLockAnnotation)
				.withFailMessage(() -> "Service contains at least one method annotated with @Scheduled, @Dept44Scheduled, and @SchedulerLock but no @EnableSchedulerLock annotation is present")
				.isTrue();
		});
	}

	private Map<String, List<Method>> getMethodsAnnotatedWith(final BeanDefinition candidate) {
		try {
			final List<Method> methods = new ArrayList<>();
			var klazz = Class.forName(candidate.getBeanClassName());
			while (klazz != Object.class) {
				for (final Method method : klazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Dept44Scheduled.class)) {
						methods.add(method);
					}
				}
				klazz = klazz.getSuperclass();
			}
			return Map.of(Objects.requireNonNull(candidate.getBeanClassName()), methods);
		} catch (final ClassNotFoundException e) {
			fail("Couldn't traverse class methods as class %s could not be found".formatted(candidate.getBeanClassName()));
			return Collections.emptyMap();
		}
	}
}
