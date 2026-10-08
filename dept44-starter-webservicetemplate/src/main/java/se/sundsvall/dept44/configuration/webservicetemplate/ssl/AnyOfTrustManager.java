package se.sundsvall.dept44.configuration.webservicetemplate.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.X509TrustManager;

/**
 * Trusts a certificate chain that at least one of the given trust managers trusts. JSSE itself only consults the first
 * {@link X509TrustManager} it is given, so several sources of trust have to be combined like this.
 */
public final class AnyOfTrustManager implements X509TrustManager {

	private final List<X509TrustManager> trustManagers;

	public AnyOfTrustManager(final List<X509TrustManager> trustManagers) {
		if (trustManagers.isEmpty()) {
			throw new IllegalArgumentException("At least one trust manager is required");
		}
		this.trustManagers = List.copyOf(trustManagers);
	}

	@Override
	public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		check(trustManager -> trustManager.checkClientTrusted(chain, authType));
	}

	@Override
	public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
		check(trustManager -> trustManager.checkServerTrusted(chain, authType));
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return trustManagers.stream()
			.map(X509TrustManager::getAcceptedIssuers)
			.flatMap(Arrays::stream)
			.toArray(X509Certificate[]::new);
	}

	private void check(final Check check) throws CertificateException {
		CertificateException failure = null;
		for (final var trustManager : trustManagers) {
			try {
				check.against(trustManager);
				return;
			} catch (final CertificateException e) {
				failure = addFailure(failure, e);
			} catch (final RuntimeException e) {
				// A trust manager without any trusted certificate, for one, fails with a RuntimeException. That must not
				// keep the others from being asked.
				failure = addFailure(failure, new CertificateException(e.getMessage(), e));
			}
		}
		throw failure;
	}

	private static CertificateException addFailure(final CertificateException failure, final CertificateException e) {
		if (failure == null) {
			return e;
		}
		failure.addSuppressed(e);
		return failure;
	}

	@FunctionalInterface
	private interface Check {
		void against(X509TrustManager trustManager) throws CertificateException;
	}
}
