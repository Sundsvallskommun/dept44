package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;

@SpringBootTest(classes = { ConfigClientBootstrapConfiguration.class, TruststoreConfiguration.class })
class ConfigClientBootstrapConfigurationTest {

	@Autowired
	private ConfigServicePropertySourceLocator configServicePropertySourceLocator;

	@Test
	void configServicePropertySourceLocatorIsAutowired() {
		assertThat(configServicePropertySourceLocator).isNotNull();
	}
}
