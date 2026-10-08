package se.sundsvall.dept44.configuration.webservicetemplate.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnyOfTrustManagerTest {

	private static final X509Certificate[] CHAIN = new X509Certificate[0];

	@Mock
	private X509TrustManager first;

	@Mock
	private X509TrustManager second;

	@Test
	void trustedWhenTheFirstTrustManagerTrusts() throws CertificateException {
		new AnyOfTrustManager(List.of(first, second)).checkServerTrusted(CHAIN, "RSA");

		verify(first).checkServerTrusted(CHAIN, "RSA");
		verifyNoInteractions(second);
	}

	@Test
	void trustedWhenALaterTrustManagerTrusts() throws CertificateException {
		doThrow(new CertificateException("first")).when(first).checkServerTrusted(CHAIN, "RSA");

		new AnyOfTrustManager(List.of(first, second)).checkServerTrusted(CHAIN, "RSA");

		verify(second).checkServerTrusted(CHAIN, "RSA");
	}

	@Test
	void notTrustedWhenNoTrustManagerTrusts() throws CertificateException {
		doThrow(new CertificateException("first")).when(first).checkServerTrusted(CHAIN, "RSA");
		doThrow(new CertificateException("second")).when(second).checkServerTrusted(CHAIN, "RSA");
		final var trustManager = new AnyOfTrustManager(List.of(first, second));

		assertThatExceptionOfType(CertificateException.class)
			.isThrownBy(() -> trustManager.checkServerTrusted(CHAIN, "RSA"))
			.withMessage("first")
			.satisfies(e -> assertThat(e.getSuppressed()).extracting(Throwable::getMessage).containsExactly("second"));
	}

	@Test
	void runtimeFailureOfOneTrustManagerDoesNotKeepTheOthersFromBeingAsked() throws CertificateException {
		doThrow(new IllegalStateException("the trustAnchors parameter must be non-empty")).when(first).checkServerTrusted(CHAIN, "RSA");

		new AnyOfTrustManager(List.of(first, second)).checkServerTrusted(CHAIN, "RSA");

		verify(second).checkServerTrusted(CHAIN, "RSA");
	}

	@Test
	void runtimeFailureIsReportedWithTheOtherFailures() throws CertificateException {
		doThrow(new CertificateException("first")).when(first).checkServerTrusted(CHAIN, "RSA");
		doThrow(new IllegalStateException("second")).when(second).checkServerTrusted(CHAIN, "RSA");
		final var trustManager = new AnyOfTrustManager(List.of(first, second));

		assertThatExceptionOfType(CertificateException.class)
			.isThrownBy(() -> trustManager.checkServerTrusted(CHAIN, "RSA"))
			.withMessage("first")
			.satisfies(e -> assertThat(e.getSuppressed())
				.singleElement()
				.satisfies(suppressed -> assertThat(suppressed).isInstanceOf(CertificateException.class).hasCauseInstanceOf(IllegalStateException.class)));
	}

	@Test
	void clientChainsAreCheckedTheSameWay() throws CertificateException {
		doThrow(new CertificateException("first")).when(first).checkClientTrusted(CHAIN, "RSA");

		new AnyOfTrustManager(List.of(first, second)).checkClientTrusted(CHAIN, "RSA");

		verify(second).checkClientTrusted(CHAIN, "RSA");
	}

	@Test
	void acceptedIssuersOfAllTrustManagers() {
		final var issuerOfFirst = mock(X509Certificate.class);
		final var issuerOfSecond = mock(X509Certificate.class);
		when(first.getAcceptedIssuers()).thenReturn(new X509Certificate[] {
			issuerOfFirst
		});
		when(second.getAcceptedIssuers()).thenReturn(new X509Certificate[] {
			issuerOfSecond
		});

		assertThat(new AnyOfTrustManager(List.of(first, second)).getAcceptedIssuers()).containsExactly(issuerOfFirst, issuerOfSecond);
	}

	@Test
	void atLeastOneTrustManagerIsRequired() {
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new AnyOfTrustManager(List.of()));
	}
}
