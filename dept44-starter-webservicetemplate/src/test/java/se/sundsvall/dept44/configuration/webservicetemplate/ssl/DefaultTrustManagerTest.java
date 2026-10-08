package se.sundsvall.dept44.configuration.webservicetemplate.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Uses the keystores in {@code tls/}: {@code unknown-server.p12} holds a self-signed certificate that the JVM default
 * trust store does not trust, and {@code trusts-unknown-server.p12} that certificate as a trusted entry.
 */
class DefaultTrustManagerTest {

	private static final String PASSWORD = "password";

	@Test
	void trustsWhatTheInstalledTruststoreTrusts() throws Exception {
		final var installed = trustingUnknownServer();
		final var trustManager = new DefaultTrustManager(() -> Optional.of(installed));
		final var chain = unknownServerChain();

		assertThatNoException().isThrownBy(() -> trustManager.checkServerTrusted(chain, "RSA"));
		assertThatNoException().isThrownBy(() -> trustManager.checkClientTrusted(chain, "RSA"));
		assertThat(trustManager.getAcceptedIssuers()).containsExactly(chain[0]);
	}

	@Test
	void trustsTheJvmDefaultTrustStoreWithoutAnInstalledTruststore() throws Exception {
		final var trustManager = new DefaultTrustManager(Optional::empty);
		final var chain = unknownServerChain();

		assertThatExceptionOfType(CertificateException.class).isThrownBy(() -> trustManager.checkServerTrusted(chain, "RSA"));
		assertThat(trustManager.getAcceptedIssuers()).isNotEmpty();
	}

	@Test
	void looksUpTheInstalledTruststoreAtEveryCheck() throws Exception {
		final var installed = new AtomicReference<TrustManagerFactory>();
		final var trustManager = new DefaultTrustManager(() -> Optional.ofNullable(installed.get()));
		final var chain = unknownServerChain();

		assertThatExceptionOfType(CertificateException.class).isThrownBy(() -> trustManager.checkServerTrusted(chain, "RSA"));
		installed.set(trustingUnknownServer());
		assertThatNoException().isThrownBy(() -> trustManager.checkServerTrusted(chain, "RSA"));
	}

	@Test
	void usesTheTruststoreInstalledByDept44ByDefault() {
		assertThat(new DefaultTrustManager().getAcceptedIssuers()).isNotNull();
	}

	@Test
	void factoryWithoutAnX509TrustManagerIsRejected() throws Exception {
		final var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

		// An uninitialized factory has no trust managers at all
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> DefaultTrustManager.x509TrustManager(factory));
	}

	private static TrustManagerFactory trustingUnknownServer() throws IOException, GeneralSecurityException {
		final var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(loadKeyStore("trusts-unknown-server.p12"));
		return factory;
	}

	private static X509Certificate[] unknownServerChain() throws IOException, GeneralSecurityException {
		return new X509Certificate[] {
			(X509Certificate) loadKeyStore("unknown-server.p12").getCertificate("unknown")
		};
	}

	private static KeyStore loadKeyStore(final String name) throws IOException, GeneralSecurityException {
		final var keyStore = KeyStore.getInstance("PKCS12");
		try (var input = new ClassPathResource("tls/" + name).getInputStream()) {
			keyStore.load(input, PASSWORD.toCharArray());
		}
		return keyStore;
	}
}
