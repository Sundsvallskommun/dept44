package se.sundsvall.dept44.configuration;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import se.sundsvall.dept44.util.jacoco.ExcludeFromJacocoGeneratedCoverageReport;

@Configuration
@ExcludeFromJacocoGeneratedCoverageReport
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

	/**
	 * Customizes the ObjectMapper-builder.
	 *
	 * The customizations implemented here are:
	 * - Disabling of the default json-attribute string length limit of 20 000 000 chars.
	 *
	 * @return Jackson2ObjectMapperBuilderCustomizer.
	 */
	@Bean
	Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
		return builder -> builder.postConfigurer(objectMapper -> objectMapper.getFactory()
			.setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build()));
	}
}
