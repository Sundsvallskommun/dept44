package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = ObjectMapperConfiguration.class)
class ObjectMapperConfigurationTest {

	@Autowired
	private YAMLMapper yamlMapper;

	@Autowired
	private JaxbAnnotationModule jaxbAnnotationModule;

	@Autowired
	private JavaTimeModule javaTimeModule;

	@Autowired
	private JsonFactory jsonFactory;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void yamlMapperIsAutowired() {
		assertThat(yamlMapper).isNotNull();
	}

	@Test
	void jaxbAnnotationModuleIsAutowired() {
		assertThat(jaxbAnnotationModule).isNotNull();
	}

	@Test
	void javaTimeModuleIsAutowired() {
		assertThat(javaTimeModule).isNotNull();
	}

	@Test
	void jsonFactoryIsAutowired() {
		assertThat(jsonFactory).isNotNull();
	}

	@Test
	void jsonMapperIsAutowired() {
		assertThat(jsonMapper).isNotNull();
	}
}
