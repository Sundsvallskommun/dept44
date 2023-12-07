package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = { LogbookConfiguration.class, ExclusionFilterProperties.class, ObjectMapperConfiguration.class })
@ActiveProfiles("junit")
class ExclusionFilterProperiesTest {

	@Autowired
	private ExclusionFilterProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.getJsonPath()).hasSize(4);
		assertThat(properties.getJsonPath().entrySet()).containsExactly(
			Map.entry("prop_path_1", "jsonpath_prop_replacement_1"),
			Map.entry("prop_path_2", "jsonpath_prop_replacement_2"),
			Map.entry("yml_path_1", "jsonpath_yml_replacement_1"),
			Map.entry("yml_path_2", "jsonpath_yml_replacement_2"));
		assertThat(properties.getXPath()).hasSize(4);
		assertThat(properties.getXPath().entrySet()).containsExactly(
			Map.entry("prop_path_1", "xpath_prop_replacement_1"),
			Map.entry("prop_path_2", "xpath_prop_replacement_2"),
			Map.entry("yml_path_1", "xpath_yml_replacement_1"),
			Map.entry("yml_path_2", "xpath_yml_replacement_2"));
	}
}
