package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Tests for ObjectMapperConfiguration.
 * <p>
 * Note: JsonMapper and JsonFactory beans are auto-configured by Spring Boot's JacksonAutoConfiguration and are not part
 * of this configuration class.
 */
@SpringBootTest(classes = ObjectMapperConfiguration.class)
class ObjectMapperConfigurationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private YAMLMapper yamlMapper;

	@Test
	void objectMapperIsAutowired() {
		assertThat(objectMapper).isNotNull();
	}

	@Test
	void yamlMapperIsAutowired() {
		assertThat(yamlMapper).isNotNull();
	}
}
