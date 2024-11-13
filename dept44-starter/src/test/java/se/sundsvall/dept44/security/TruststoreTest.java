package se.sundsvall.dept44.security;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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
		assertThat(truststore.getTrustManagerFactory()).isNotNull();
	}

	@Test
	void createWhenNoCertificatesFoundInPaths() {
		try (
			MockedConstruction<PathMatchingResourcePatternResolver> pathMatchingResourcePatternResolverConstructionMock = Mockito
				.mockConstruction(PathMatchingResourcePatternResolver.class,
					(mock, context) -> when(mock.getResources(anyString())).thenThrow(new IOException()))) {
			final var truststore = new Truststore("dummy");

			assertThat(truststore).isNotNull();
			assertThat(truststore.getSSLContext()).isNotNull();
			assertThat(truststore.getTrustManagerFactory()).isNotNull();
		}
	}
}
