package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

@SpringBootTest(classes = ObjectMapperConfiguration.class)
class ObjectMapperConfigurationTest {

	@Autowired
	private YAMLMapper yamlMapper;

	@Autowired
	private JaxbAnnotationModule jaxbAnnotationModule;

	@Autowired
	private JavaTimeModule javaTimeModule;

	@Autowired
	private Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer;

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
	void jackson2ObjectMapperBuilderCustomizerIsAutowired() {
		assertThat(jackson2ObjectMapperBuilderCustomizer).isNotNull();
	}
}
