package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest(classes = ObjectMapperConfiguration.class)
class ObjectMapperConfigurationTest {

	@Autowired
	private YAMLMapper yamlMapper;

	@Autowired
	private JakartaXmlBindAnnotationModule jakartaXmlBindAnnotationModule;

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
		assertThat(jakartaXmlBindAnnotationModule).isNotNull();
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
