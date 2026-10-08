package se.sundsvall.dept44.configuration.webservicetemplate.ssl;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import se.sundsvall.dept44.security.Truststore;

/**
 * Trusts the servers the rest of the application trusts: those of the dept44 truststore installed as the JVM default,
 * or, when there is none, those of the JVM's own default trust store. The truststore is looked up at every check, so it
 * does not matter whether it is set up before or after the client is built.
 */
public final class DefaultTrustManager implements X509TrustManager {

	private static final X509TrustManager JVM_DEFAULT = jvmDefault();

	private final Supplier<Optional<TrustManagerFactory>> installed;

	public DefaultTrustManager() {
		this(Truststore::installedTrustManagerFactory);
	}

	DefaultTrustManager(final Supplier<Optional<TrustManagerFactory>> installed) {
		this.installed = installed;
	}

	@Override
	public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		delegate().checkClientTrusted(chain, authType);
	}

	@Override
	public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		delegate().checkServerTrusted(chain, authType);
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return delegate().getAcceptedIssuers();
	}

	/**
	 * The X.509 trust manager of the given factory.
	 *
	 * @param  factory an initialized trust manager factory
	 * @return         its X.509 trust manager
	 */
	public static X509TrustManager x509TrustManager(final TrustManagerFactory factory) {
		return Arrays.stream(factory.getTrustManagers())
			.filter(X509TrustManager.class::isInstance)
			.map(X509TrustManager.class::cast)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("The trust manager factory has no X.509 trust manager"));
	}

	private X509TrustManager delegate() {
		return installed.get()
			.map(DefaultTrustManager::x509TrustManager)
			.orElse(JVM_DEFAULT);
	}

	private static X509TrustManager jvmDefault() {
		try {
			final var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			factory.init((KeyStore) null);
			return x509TrustManager(factory);
		} catch (final GeneralSecurityException e) {
			throw new IllegalStateException("Could not load the JVM default trust store", e);
		}
	}
}
