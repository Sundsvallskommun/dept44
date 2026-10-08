package se.sundsvall.dept44.security;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TruststoreTest {

	@Test
	void createWithWrongPath() {
		final var truststore = new Truststore("dummy");
		assertThat(truststore).isNotNull();
		assertThat(truststore.getSSLContext()).isNotNull();
		assertThat(truststore.getTrustManagerFactory()).isNotNull();
	}

	@Test
	void createWithWorkingPath() throws NoSuchAlgorithmException {
		final var defaultSSLContext = SSLContext.getDefault();
		final var truststore = new Truststore("internal-truststore/*", "");

		// If something goes wrong SSLContext is set to defaultSSLContext
		assertThat(truststore.getSSLContext()).isNotEqualTo(defaultSSLContext);
		assertThat(truststore.getSSLContext()).isNotNull();
		assertThat(truststore.getSSLContext().getDefaultSSLParameters().getProtocols()).contains("TLSv1.3", "TLSv1.2");
		assertThat(truststore.getTrustManagerFactory()).isNotNull();
		assertThat(Truststore.installedTrustManagerFactory()).containsSame(truststore.getTrustManagerFactory());
	}

	@Test
	void createWhenNoCertificatesFoundInPaths() {
		try (var _ = Mockito.mockConstruction(PathMatchingResourcePatternResolver.class,
			(mock, _) -> when(mock.getResources(anyString())).thenThrow(new IOException()))) {
			final var truststore = new Truststore("dummy");

			assertThat(truststore).isNotNull();
			assertThat(truststore.getSSLContext()).isNotNull();
			assertThat(truststore.getTrustManagerFactory()).isNotNull();
		}
	}
}
