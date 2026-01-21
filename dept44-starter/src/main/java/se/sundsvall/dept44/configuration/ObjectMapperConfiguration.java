package se.sundsvall.dept44.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import se.sundsvall.dept44.util.jacoco.ExcludeFromJacocoGeneratedCoverageReport;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Jackson configuration.
 * <p>
 * This class provides: - JsonMapperBuilderCustomizer for Jackson 3.x DateTimeFeature configuration - Jackson 2.x
 * ObjectMapper for libraries that haven't migrated (e.g., jayway/jsonpath) - YAMLMapper for YAML processing
 */
@AutoConfiguration
@AutoConfigureBefore(JacksonAutoConfiguration.class)
@ExcludeFromJacocoGeneratedCoverageReport
public class ObjectMapperConfiguration {
	/**
	 * Customizer for Jackson 3.x JsonMapper to configure DateTimeFeature settings.
	 * <p>
	 * Disables timezone normalization to preserve original timezone offsets: - ADJUST_DATES_TO_CONTEXT_TIME_ZONE: preserves
	 * timezone on deserialization - WRITE_DATES_WITH_CONTEXT_TIME_ZONE: preserves timezone on serialization -
	 * WRITE_DATES_AS_TIMESTAMPS:
	 * writes ISO-8601 strings instead of timestamps
	 *
	 * @return the JsonMapperBuilderCustomizer
	 */
	@Bean
	JsonMapperBuilderCustomizer dateTimeFeatureCustomizer() {
		return builder -> builder
			.disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
			.disable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
			.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	/**
	 * Jackson 2.x ObjectMapper bean for libraries that haven't migrated to Jackson 3.x yet (e.g., jayway/jsonpath used in
	 * BodyFilterProvider for Logbook).
	 *
	 * @return the ObjectMapper
	 */
	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper()
			.findAndRegisterModules()
			.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
	}

	@Bean
	YAMLMapper yamlMapper() {
		return YAMLMapper.builder().findAndAddModules().build();
	}
}
