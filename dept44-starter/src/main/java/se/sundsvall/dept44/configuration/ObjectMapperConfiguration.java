package se.sundsvall.dept44.configuration;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.springframework.context.annotation.Bean;

public class ObjectMapperConfiguration {

	@Bean
	JaxbAnnotationModule jaxbAnnotationModule() {
		return new JaxbAnnotationModule();
	}

	@Bean
	JavaTimeModule javaTimeModule() {
		return new JavaTimeModule();
	}

	@Bean
	YAMLMapper yamlMapper() {
		return new YAMLMapper();
	}
}
