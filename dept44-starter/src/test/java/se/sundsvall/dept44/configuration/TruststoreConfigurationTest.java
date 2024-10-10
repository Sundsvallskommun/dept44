package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import se.sundsvall.dept44.security.Truststore;

@SpringBootTest(classes = TruststoreConfiguration.class)
class TruststoreConfigurationTest {

	@Autowired
	private Truststore truststore;

	@Test
	void truststoreIsAutowired() {
		assertThat(truststore).isNotNull();
	}
}
