package se.sundsvall.dept44.configuration;

import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

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
