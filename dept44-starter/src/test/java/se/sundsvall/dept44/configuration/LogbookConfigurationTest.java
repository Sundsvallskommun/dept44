package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.zalando.logbook.Logbook;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = LogbookConfiguration.class)
class LogbookConfigurationTest {

	@MockBean
	private ObjectMapper mockObjectMapper;

	@Autowired
	private Logbook logbook;

	@Test
	void testAutowiredLogbook() {
		assertThat(logbook).isNotNull();
	}
}
