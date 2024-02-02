package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zalando.logbook.Logbook;

@SpringBootTest(classes = { LogbookConfiguration.class, ExclusionFilterProperties.class, ObjectMapperConfiguration.class, BodyFilterProperties.class})
class LogbookConfigurationTest {

	@Autowired
	private Logbook logbook;

	@Test
	void testAutowiredLogbook() {
		assertThat(logbook).isNotNull();
	}
}
