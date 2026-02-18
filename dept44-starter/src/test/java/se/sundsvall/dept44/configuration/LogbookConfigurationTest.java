package se.sundsvall.dept44.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zalando.logbook.Logbook;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
	LogbookConfiguration.class, ObjectMapperConfiguration.class, BodyFilterProperties.class
})
class LogbookConfigurationTest {

	@Autowired
	private Logbook logbook;

	@Test
	void testAutowiredLogbook() {
		assertThat(logbook).isNotNull();
	}
}
