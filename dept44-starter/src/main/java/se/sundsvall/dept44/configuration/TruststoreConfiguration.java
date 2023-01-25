package se.sundsvall.dept44.configuration;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import se.sundsvall.dept44.security.Truststore;

public class TruststoreConfiguration {

	@Value("${dept44.truststore.path:truststore/*}")
	private String trustStorePath;

	@Bean
	@Scope(value = SCOPE_SINGLETON)
	Truststore truststore() {
		return new Truststore(trustStorePath);
	}
}
