package se.sundsvall.dept44.scheduling.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Dept44ScheduledLockValidatorTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(LockConfiguration.class)
		.withPropertyValues("lock.at-least-for=PT1M");

	@Test
	void lockAtLeastForLongerThanTheDefaultLockAtMostForFailsTheStartup() {
		contextRunner.withBean(TooLongAtLeast.class).run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("lockAtLeastFor"));
		contextRunner.withBean(TooLongAtLeast.class).run(context -> assertThat(context.getStartupFailure())
			.hasMessageContaining("Invalid lock settings on scheduled method TooLongAtLeast.run"));
	}

	@Test
	void validLockSettingsStartUp() {
		contextRunner.withBean(Valid.class).run(context -> assertThat(context).hasNotFailed().hasSingleBean(Valid.class));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")
	static class LockConfiguration {

		@Bean
		LockProvider lockProvider() {
			return mock(LockProvider.class);
		}

		@Bean
		Dept44ScheduledLockValidator dept44ScheduledLockValidator(final ConfigurableListableBeanFactory beanFactory, final ExtendedLockConfigurationExtractor lockConfigurationExtractor) {
			return new Dept44ScheduledLockValidator(beanFactory, lockConfigurationExtractor);
		}
	}

	static class TooLongAtLeast {

		@Dept44Scheduled(cron = "0 0 * * * *", name = "too-long", lockAtLeastFor = "PT5M")
		public void run() {
			// Never runs in this test
		}
	}

	static class Valid {

		@Dept44Scheduled(cron = "0 0 * * * *", name = "valid", lockAtMostFor = "10m", lockAtLeastFor = "${lock.at-least-for}")
		public void run() {
			// Never runs in this test
		}
	}
}
