package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = { LogbookConfiguration.class, BodyFilterProperties.class, ObjectMapperConfiguration.class })
@ActiveProfiles("junit")
class BodyFilterPropertiesTest {

	@Autowired
	BodyFilterProperties properties;


	@Test
	void testProperties(){

		assertThat(properties.getJsonPath()).hasSize(2);
		assertThat(properties.getxPath()).hasSize(2);

		assertThat(properties.getJsonPath().getFirst().entrySet()).containsExactly(
			Map.entry("key", "some_yml_jsonpath_key_1"),
			Map.entry("value", "some_yml_jsonpath_value_1")
		);
		assertThat(properties.getJsonPath().get(1).entrySet()).containsExactly(
			Map.entry("key", "some_yml_jsonpath_key_2"),
			Map.entry("value", "some_yml_jsonpath_value_2"));
		assertThat(properties.getxPath().getFirst().entrySet()).containsExactly(
			Map.entry("key", "some_yml_xpath_key_1"),
			Map.entry("value", "some_yml_xpath_value_1"));
	}
}
