package se.sundsvall.dept44.configuration.feign;

import feign.Client;
import feign.Logger;
import feign.okhttp.OkHttpClient;
import javax.net.ssl.X509TrustManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import se.sundsvall.dept44.security.Truststore;

public class FeignConfiguration {

	@Bean
	Logger.Level logLevel() {
		return Logger.Level.FULL;
	}

	@Bean
	@ConditionalOnBean(Truststore.class)
	Client okHttpClient(final Truststore trustStore) {

		final var trustManagerFactory = trustStore.getTrustManagerFactory();
		final var trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];

		return new OkHttpClient(new okhttp3.OkHttpClient.Builder()
			.sslSocketFactory(trustStore.getSSLContext().getSocketFactory(), trustManager)
			.build());
	}
}
