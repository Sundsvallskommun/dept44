package se.sundsvall.dept44.maven.mojo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import static java.time.format.DateTimeFormatter.ISO_DATE;

@Mojo(name = "check-truststore-validity", defaultPhase = LifecyclePhase.INITIALIZE)
public class CheckTruststoreValidityMojo extends AbstractDept44CheckMojo {

	private static final String FAILURE_MESSAGE = "Certificate '%s' expiration date (%s) is before or less than %d months from now (%s) and needs to be updated";
	private static final List<String> CERTIFICATE_EXTENSIONS = List.of(".pem", ".crt", ".cer", ".der");

	private final CertificateFactory certificateFactory;

	private boolean skip;
	private String truststorePath;
	private int monthsUntilExpiration;

	public CheckTruststoreValidityMojo() throws MojoFailureException {
		try {
			certificateFactory = CertificateFactory.getInstance("X509");
		} catch (CertificateException e) {
			throw new MojoFailureException("Unable to obtain certificate factory", e);
		}
	}

	@Override
	public void doExecute() throws MojoFailureException {
		if (isSkipAllChecks() || skip) {
			getLog().info("Skipping expiration check for truststore certificates");

			return;
		}

		getLog().info("Checking expiration for truststore certificates");

		try {
			var truststoreDir = new File(getProject().getBasedir(), "src/main/resources/" + truststorePath);

			var today = LocalDate.now(ZoneId.systemDefault());
			var expiry = today.plusMonths(monthsUntilExpiration);

			var certificateFiles = truststoreDir.listFiles(File::isFile);
			if (certificateFiles != null) {
				for (var certificateFile : certificateFiles) {
					checkCertificate(certificateFile, today, expiry);
				}
			}
		} catch (IOException e) {
			throw new MojoFailureException("Unable to check certificates " + e.getLocalizedMessage(), e);
		}
	}

	private void checkCertificate(final File certificateFile, final LocalDate today, final LocalDate expiry) throws IOException {
		try (var certificateInputStream = new FileInputStream(certificateFile)) {
			var certificate = (X509Certificate) certificateFactory.generateCertificate(certificateInputStream);
			var notAfter = certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			if (notAfter.isBefore(expiry)) {
				addError(FAILURE_MESSAGE.formatted(certificateFile.getName(), notAfter.format(ISO_DATE), monthsUntilExpiration, today.format(ISO_DATE)));
			}
		} catch (final CertificateException e) {
			if (hasCertificateExtension(certificateFile)) {
				// Meant to be a certificate, so a damaged one: the runtime truststore would silently leave it out
				addError("Certificate '%s' could not be read: %s".formatted(certificateFile.getName(), e.getMessage()));
			} else {
				// Not meant to be one, such as .gitkeep, .DS_Store or a README; the runtime truststore skips it too
				getLog().warn("Skipping '%s', which is not an X.509 certificate: %s".formatted(certificateFile.getName(), e.getMessage()));
			}
		}
	}

	private static boolean hasCertificateExtension(final File file) {
		final var name = file.getName().toLowerCase(Locale.ROOT);
		return CERTIFICATE_EXTENSIONS.stream().anyMatch(name::endsWith);
	}

	@Parameter(property = "dept44.check.truststore.skip", defaultValue = "false")
	public void setSkip(final boolean skip) {
		this.skip = skip;
	}

	@Parameter(property = "dept44.check.truststore.path", defaultValue = "truststore/")
	public void setTruststorePath(final String truststorePath) {
		this.truststorePath = truststorePath;
	}

	@Parameter(property = "dept44.check.truststore.months-until-expiration", defaultValue = "1")
	public void setMonthsUntilExpiration(final int monthsUntilExpiration) {
		this.monthsUntilExpiration = monthsUntilExpiration;
	}
}
