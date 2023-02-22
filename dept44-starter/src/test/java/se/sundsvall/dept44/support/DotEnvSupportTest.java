package se.sundsvall.dept44.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import se.sundsvall.dept44.support.testutil.TestApplication;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("junit")
class DotEnvSupportTest {

	/*****************************************
	 * From .env file
	 *****************************************/
	@Value("${this.is.an.env.key:}")
	private String envValueSpringStyle;

	@Value("${ENV_KEY_1:}")
	private String envValue1;

	@Value("${ENV_KEY_2:}")
	private String envValue2;

	/*****************************************
	 * From properties file (junit)
	 *****************************************/
	@Value("${property.key.spring.style:}")
	private String propertyValueSpringStyle;

	@Value("${property.key.1:}")
	private String propertyValue1;

	@Value("${property.key.2:}")
	private String propertyValue2;

	/*****************************************
	 * Should be empty (test legacy prefixing)
	 *****************************************/
	@Value("${env.ENV_KEY_1:}")
	private String envValueLegacyPrefixed1;

	@Value("${env.ENV_KEY_2:}")
	private String envValueLegacyPrefixed2;

	@Test
	void readEnvConfig() {
		assertThat(envValueSpringStyle).isEqualTo("My value");
		assertThat(envValue1).isEqualTo("Message 1 from .env");
		assertThat(envValue2).isEqualTo("Message 2 from .env");
	}

	@Test
	void readApplicationPropertiesConfig() {
		assertThat(propertyValueSpringStyle).isEqualTo("My value (embedded in properties file)");
		assertThat(propertyValue1).isEqualTo("Message 1 from .env (embedded in properties file)");
		assertThat(propertyValue2).isEqualTo("Message 2 from .env (embedded in properties file)");
	}

	@Test
	void readEnvConfigLegacyPrefixed() {
		assertThat(envValueLegacyPrefixed1).isEmpty();
		assertThat(envValueLegacyPrefixed2).isEmpty();
	}
}
