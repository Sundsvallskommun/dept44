package se.sundsvall.dept44.configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.autoconfigure.application.SslHealthContributorAutoConfiguration;
import org.springframework.boot.health.autoconfigure.application.SslHealthIndicatorProperties;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.info.SslInfo;
import org.springframework.boot.info.SslInfo.CertificateChainInfo;
import org.springframework.boot.info.SslInfo.CertificateInfo;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.boot.health.contributor.Status.OUT_OF_SERVICE;
import static org.springframework.boot.health.contributor.Status.UP;
import static se.sundsvall.dept44.configuration.HealthConfiguration.RESTRICTED;

/**
 * Replaces the default {@link org.springframework.boot.health.application.SslHealthIndicator}
 * with one that also reports {@link Status#OUT_OF_SERVICE} when certificates are expiring
 * (within the configured warning threshold), not just when they are already invalid.
 *
 * <p>
 * This configuration is activated only when all of the following conditions are met:
 * <ul>
 * <li>{@link SslBundles} bean is present — i.e. the application has SSL bundles configured
 * (services without SSL context are unaffected)</li>
 * <li>The {@code ssl} health indicator is enabled (controlled by
 * {@code management.health.ssl.enabled}, defaults to {@code true})</li>
 * <li>The property {@code dept44.ssl.health.disabled} is not set to {@code true}
 * (defaults to {@code false}, meaning this indicator is active unless explicitly disabled)</li>
 * </ul>
 *
 * <p>
 * The expiry warning threshold is controlled by
 * {@code management.health.ssl.certificate-validity-warning-threshold} (e.g. {@code 30d}).
 */
@Configuration
@AutoConfiguration(before = SslHealthContributorAutoConfiguration.class)
@ConditionalOnBean(SslBundles.class)
@ConditionalOnEnabledHealthIndicator("ssl")
@ConditionalOnProperty(name = "dept44.ssl.health.disabled", havingValue = "false", matchIfMissing = true)
class SslHealthIndicatorConfiguration {

	@Bean
	HealthIndicator sslHealthIndicator(final SslInfo sslInfo, final SslHealthIndicatorProperties properties) {
		return new ExpiryAwareSslHealthIndicator(sslInfo, properties.getCertificateValidityWarningThreshold());
	}

	static class ExpiryAwareSslHealthIndicator extends AbstractHealthIndicator {

		private final SslInfo sslInfo;

		private final Duration expiryThreshold;

		ExpiryAwareSslHealthIndicator(final SslInfo sslInfo, final Duration expiryThreshold) {
			super("SSL health check failed");
			this.sslInfo = sslInfo;
			this.expiryThreshold = expiryThreshold;
		}

		@Override
		protected void doHealthCheck(final Health.@NonNull Builder builder) {
			final var validCertificateChains = new ArrayList<CertificateChainInfo>();
			final var invalidCertificateChains = new ArrayList<CertificateChainInfo>();
			final var expiringCertificateChains = new ArrayList<CertificateChainInfo>();

			for (final var bundle : sslInfo.getBundles()) {
				for (final var certificateChain : bundle.getCertificateChains()) {
					if (containsOnlyValidCertificates(certificateChain)) {
						validCertificateChains.add(certificateChain);
						if (containsExpiringCertificate(certificateChain)) {
							expiringCertificateChains.add(certificateChain);
						}
					} else if (containsInvalidCertificate(certificateChain)) {
						invalidCertificateChains.add(certificateChain);
					}
				}
			}

			final var hasCertificateAboutToExpire = !expiringCertificateChains.isEmpty();
			final var hasInvalidCertificate = !invalidCertificateChains.isEmpty();

			builder.status(UP);

			if (hasCertificateAboutToExpire) {
				builder.status(RESTRICTED);
			}

			if (hasInvalidCertificate) {
				builder.status(OUT_OF_SERVICE);
			}

			builder.withDetail("expiringChains", expiringCertificateChains);
			builder.withDetail("invalidChains", invalidCertificateChains);
			builder.withDetail("validChains", validCertificateChains);
		}

		private boolean containsOnlyValidCertificates(final CertificateChainInfo certificateChain) {
			return validatableCertificates(certificateChain).allMatch(this::isValidCertificate);
		}

		private boolean containsInvalidCertificate(final CertificateChainInfo certificateChain) {
			return validatableCertificates(certificateChain).anyMatch(cert -> !isValidCertificate(cert));
		}

		private boolean containsExpiringCertificate(final CertificateChainInfo certificateChain) {
			return validatableCertificates(certificateChain).anyMatch(this::isExpiringCertificate);
		}

		private Stream<CertificateInfo> validatableCertificates(final CertificateChainInfo certificateChain) {
			return certificateChain.getCertificates().stream().filter(cert -> cert.getValidity() != null);
		}

		private boolean isValidCertificate(final CertificateInfo certificate) {
			return Optional.ofNullable(certificate.getValidity())
				.map(SslInfo.CertificateValidityInfo::getStatus)
				.map(SslInfo.CertificateValidityInfo.Status::isValid)
				.orElse(false);
		}

		private boolean isExpiringCertificate(final CertificateInfo certificate) {
			return Optional.ofNullable(certificate)
				.map(CertificateInfo::getValidityEnds)
				.map(sadf -> Instant.now().plus(expiryThreshold).isAfter(sadf))
				.orElse(false);
		}
	}
}
