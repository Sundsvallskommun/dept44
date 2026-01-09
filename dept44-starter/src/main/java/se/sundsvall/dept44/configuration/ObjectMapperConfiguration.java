package se.sundsvall.dept44.configuration;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import java.util.List;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.sundsvall.dept44.util.jacoco.ExcludeFromJacocoGeneratedCoverageReport;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

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
	 * Custom JsonFactory bean with adjusted StreamReadConstraints to allow for larger strings.
	 *
	 * @return the customized JsonFactory
	 */

	@Bean
	JsonFactory jsonFactory() {
		return JsonFactory.builder()
			.streamReadConstraints(StreamReadConstraints.builder()
				.maxStringLength(Integer.MAX_VALUE)
				.build())
			.build();
	}

	/**
	 * Custom JsonMapper bean that applies all JsonMapperBuilderCustomizers.
	 *
	 * @param  jsonFactory the custom JsonFactory bean
	 * @param  customizers the list of JsonMapperBuilderCustomizers
	 * @return             the customized JsonMapper
	 */
	@Bean
	JsonMapper jsonMapper(final JsonFactory jsonFactory, final List<JsonMapperBuilderCustomizer> customizers) {
		final JsonMapper.Builder builder = JsonMapper.builder(jsonFactory);
		customizers.forEach(c -> c.customize(builder));
		return builder.build();
	}
}
