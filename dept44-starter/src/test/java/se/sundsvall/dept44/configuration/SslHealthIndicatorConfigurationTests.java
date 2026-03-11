package se.sundsvall.dept44.configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.autoconfigure.application.SslHealthIndicatorProperties;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.info.SslInfo;
import org.springframework.boot.info.SslInfo.BundleInfo;
import org.springframework.boot.info.SslInfo.CertificateChainInfo;
import org.springframework.boot.info.SslInfo.CertificateInfo;
import org.springframework.boot.info.SslInfo.CertificateValidityInfo;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import se.sundsvall.dept44.configuration.SslHealthIndicatorConfiguration.ExpiryAwareSslHealthIndicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.dept44.configuration.HealthConfiguration.RESTRICTED;

@ExtendWith(MockitoExtension.class)
class SslHealthIndicatorConfigurationTests {

	private static final Duration THRESHOLD = Duration.ofDays(14);

	@Mock
	private SslInfo sslInfo;

	@Mock
	private SslHealthIndicatorProperties properties;

	@Mock
	private BundleInfo bundleInfo;

	@Mock
	private CertificateChainInfo certificateChainInfo;

	@Mock
	private CertificateInfo certificateInfo;

	@Mock
	private CertificateValidityInfo certificateValidityInfo;

	@InjectMocks
	private SslHealthIndicatorConfiguration configuration;

	@AfterEach
	void tearDown() {
		verifyNoMoreInteractions(properties, sslInfo, bundleInfo, certificateChainInfo, certificateInfo, certificateValidityInfo);
	}

	@Test
	void healthIsUpWhenNoCertificateIssues() {
		setupCertificate(CertificateValidityInfo.Status.VALID, Instant.now().plus(Duration.ofDays(30)));

		final var indicator = configuration.sslHealthIndicator(sslInfo, properties);
		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsKey("validChains");

		verify(properties).getCertificateValidityWarningThreshold();
		verify(sslInfo).getBundles();
		verify(bundleInfo).getCertificateChains();
		verify(certificateChainInfo, times(2)).getCertificates();
		verify(certificateInfo, times(3)).getValidity();
		verify(certificateValidityInfo).getStatus();
		verify(certificateInfo).getValidityEnds();
	}

	@Test
	void healthIsOutOfServiceWhenCertificateIsExpired() {
		setupCertificate(CertificateValidityInfo.Status.EXPIRED, Instant.now().minus(Duration.ofDays(1)));

		final var indicator = configuration.sslHealthIndicator(sslInfo, properties);
		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
		assertThat((List<?>) health.getDetails().get("invalidChains")).hasSize(1);

		verify(properties).getCertificateValidityWarningThreshold();
		verify(sslInfo).getBundles();
		verify(bundleInfo).getCertificateChains();
		verify(certificateChainInfo, times(2)).getCertificates();
		verify(certificateInfo, times(4)).getValidity();
		verify(certificateValidityInfo, times(2)).getStatus();
	}

	@Test
	void healthIsOutOfServiceWhenCertificateIsExpiring() {
		setupCertificate(CertificateValidityInfo.Status.VALID, Instant.now().plus(Duration.ofDays(7)));

		final var indicator = configuration.sslHealthIndicator(sslInfo, properties);
		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(RESTRICTED);
		assertThat((List<?>) health.getDetails().get("expiringChains")).hasSize(1);

		verify(properties).getCertificateValidityWarningThreshold();
		verify(sslInfo).getBundles();
		verify(bundleInfo).getCertificateChains();
		verify(certificateChainInfo, times(2)).getCertificates();
		verify(certificateInfo, times(3)).getValidity();
		verify(certificateValidityInfo).getStatus();
		verify(certificateInfo).getValidityEnds();
	}

	@Test
	void healthIsUpWhenNoBundles() {
		when(properties.getCertificateValidityWarningThreshold()).thenReturn(THRESHOLD);
		when(sslInfo.getBundles()).thenReturn(List.of());

		final var indicator = configuration.sslHealthIndicator(sslInfo, properties);
		final var health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);

		verify(properties).getCertificateValidityWarningThreshold();
		verify(sslInfo).getBundles();
	}

	private void setupCertificate(final CertificateValidityInfo.Status validityStatus, final Instant validityEnds) {
		when(properties.getCertificateValidityWarningThreshold()).thenReturn(THRESHOLD);
		when(sslInfo.getBundles()).thenReturn(List.of(bundleInfo));
		when(bundleInfo.getCertificateChains()).thenReturn(List.of(certificateChainInfo));
		when(certificateChainInfo.getCertificates()).thenReturn(List.of(certificateInfo));
		when(certificateInfo.getValidity()).thenReturn(certificateValidityInfo);
		when(certificateValidityInfo.getStatus()).thenReturn(validityStatus);

		if (validityStatus.isValid()) {
			when(certificateInfo.getValidityEnds()).thenReturn(validityEnds);
		}
	}

	/**
	 * Tests for the auto-configuration conditions of {@link SslHealthIndicatorConfiguration}.
	 */
	@Nested
	class AutoConfigurationConditions {

		private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SslHealthIndicatorConfiguration.class));

		// Verifies ExpiryAwareSslHealthIndicator is created when SslBundles, SslInfo, and SslHealthIndicatorProperties are
		// present
		@Test
		void beanCreatedWhenAllConditionsMet() {
			contextRunner
				.withBean(SslBundles.class, () -> mock(SslBundles.class))
				.withBean(SslInfo.class, () -> mock(SslInfo.class))
				.withBean(SslHealthIndicatorProperties.class, SslHealthIndicatorProperties::new)
				.run(context -> assertThat(context)
					.hasSingleBean(ExpiryAwareSslHealthIndicator.class));
		}

		// no SslBundles bean, no custom health indicator
		@Test
		void beanNotCreatedWhenNoSslBundles() {
			contextRunner
				.run(context -> assertThat(context)
					.doesNotHaveBean(ExpiryAwareSslHealthIndicator.class));
		}

		// management.health.ssl.enabled=false, no indicator
		@Test
		void beanNotCreatedWhenSslHealthIndicatorDisabled() {
			contextRunner
				.withBean(SslBundles.class, () -> mock(SslBundles.class))
				.withBean(SslInfo.class, () -> mock(SslInfo.class))
				.withPropertyValues("management.health.ssl.enabled=false")
				.run(context -> assertThat(context)
					.doesNotHaveBean(ExpiryAwareSslHealthIndicator.class));
		}

		// dept44.ssl.health.disabled=true, no indicator
		@Test
		void beanNotCreatedWhenDept44SslHealthDisabled() {
			contextRunner
				.withBean(SslBundles.class, () -> mock(SslBundles.class))
				.withBean(SslInfo.class, () -> mock(SslInfo.class))
				.withPropertyValues("dept44.ssl.health.disabled=true")
				.run(context -> assertThat(context)
					.doesNotHaveBean(ExpiryAwareSslHealthIndicator.class));
		}
	}
}
