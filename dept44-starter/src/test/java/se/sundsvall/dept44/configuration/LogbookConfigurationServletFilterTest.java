package se.sundsvall.dept44.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.servlet.LogbookServletFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs together with Logbook's own auto-configuration, which must back off for the servlet filters replaced here.
 */
class LogbookConfigurationServletFilterTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperConfiguration.class, LogbookConfiguration.class,
			LogbookAutoConfiguration.class))
		.withPropertyValues("logbook.default.excluded.paths=/actuator/**");

	@Test
	void logbookServletFiltersAreReplaced() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean("logbookFilter", FilterRegistrationBean.class).getFilter()).isInstanceOf(LogbookServletFilter.class);
			assertThat(context.getBean("secureLogbookFilter", FilterRegistrationBean.class).getFilter()).isInstanceOf(LogbookServletFilter.class);
		});
	}

	@Test
	void filtersCanBeDisabled() {
		contextRunner
			.withPropertyValues("logbook.filter.enabled=false", "logbook.secure-filter.enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean("logbookFilter");
				assertThat(context).doesNotHaveBean("secureLogbookFilter");
			});
	}

	@Test
	void captureLimitIsConfigurable() {
		contextRunner
			.withPropertyValues("logbook.logs.maxBodySizeToCapture=-1")
			.run(context -> assertThat(context.getBean(BodyCapturePolicy.class).isLimited()).isFalse());
	}
}
