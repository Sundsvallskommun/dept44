package se.sundsvall.dept44.configuration;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import se.sundsvall.dept44.security.Truststore;

/**
 * Configuration class necessary for configuration of config client bootstrap loading.
 * 
 * A custom RestTemplate is used with:
 * - Truststore SSL-context activated.
 * - Hostname verification disabled
 * 
 * Note: This class must be added to org.springframework.cloud.bootstrap.BootstrapConfiguration in spring.factories
 * 
 * @see se.sundsvall.dept44.security.Truststore
 */
@Configuration
public class ConfigClientBootstrapConfiguration {

	@Bean
	ConfigServicePropertySourceLocator configServicePropertySourceLocator(Truststore truststore, @Autowired ConfigClientProperties configClientProperties) {

		final var httpClient = HttpClients.custom()
			.setSSLHostnameVerifier(new NoopHostnameVerifier())
			.setSSLContext(truststore.getSSLContext())
			.build();

		final var configServicePropertySourceLocator = new ConfigServicePropertySourceLocator(configClientProperties);
		configServicePropertySourceLocator.setRestTemplate(new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient)));

		return configServicePropertySourceLocator;
	}
}
