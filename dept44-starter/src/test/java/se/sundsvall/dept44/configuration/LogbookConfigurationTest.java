package se.sundsvall.dept44.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.servlet.LogbookFilter;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.servlet.LogbookServletFilter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
	JacksonAutoConfiguration.class, LogbookConfiguration.class, ObjectMapperConfiguration.class, BodyFilterProperties.class
})
class LogbookConfigurationTest {

	@Autowired
	private Logbook logbook;

	@Autowired
	private BodyCapturePolicy bodyCapturePolicy;

	@Autowired
	@Qualifier("logbookFilter")
	private FilterRegistrationBean<?> logbookFilter;

	@Autowired
	@Qualifier("secureLogbookFilter")
	private FilterRegistrationBean<?> secureLogbookFilter;

	@Test
	void testAutowiredLogbook() {
		assertThat(logbook).isNotNull();
	}

	@Test
	void testBodyCapturePolicyDefaultsToOneMegabyte() {
		assertThat(bodyCapturePolicy.maxBodySize()).isEqualTo(1024 * 1024);
	}

	@Test
	void testServletFilters() {
		assertThat(logbookFilter.getFilter()).isInstanceOf(LogbookServletFilter.class);
		assertThat(logbookFilter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
		assertThat(secureLogbookFilter.getFilter()).isInstanceOf(LogbookFilter.class);
		assertThat(secureLogbookFilter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
	}
}
