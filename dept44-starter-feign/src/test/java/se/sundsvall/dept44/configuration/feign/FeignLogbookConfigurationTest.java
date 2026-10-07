package se.sundsvall.dept44.configuration.feign;

import feign.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;
import se.sundsvall.dept44.configuration.feign.logbook.BodyCaptureFeignLogger;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeignLogbookConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, FeignLogbookConfiguration.class, LogbookAutoConfiguration.class))
		.withBean(Logbook.class, () -> mock(Logbook.class))
		.withBean(BodyCapturePolicy.class, () -> new BodyCapturePolicy(100));

	@Test
	void replacesLogbookFeignLogger() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(Logger.class);
			assertThat(context.getBean(Logger.class)).isInstanceOf(BodyCaptureFeignLogger.class);
		});
	}

	@Test
	void backsOffForCustomLogger() {
		contextRunner
			.withBean("customFeignLogger", Logger.class, Logger.NoOpLogger::new)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(Logger.class);
				assertThat(context.getBean(Logger.class)).isInstanceOf(Logger.NoOpLogger.class);
			});
	}
}
