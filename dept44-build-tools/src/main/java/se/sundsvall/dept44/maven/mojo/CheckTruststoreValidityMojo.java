package se.sundsvall.dept44.maven.mojo;

import static java.time.format.DateTimeFormatter.ISO_DATE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "check-truststore-validity", defaultPhase = LifecyclePhase.INITIALIZE)
public class CheckTruststoreValidityMojo extends AbstractDept44CheckMojo {

    private static final String FAILURE_MESSAGE = "Certificate '%s' expiration date (%s) is before or less than %d months from now (%s) and needs to be updated";

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

            var today = LocalDate.now();
            var expiry = today.plusMonths(monthsUntilExpiration);

            var certificateFiles = truststoreDir.listFiles(File::isFile);
            if (certificateFiles != null) {
                for (var certificateFile : certificateFiles) {
                    try (var certificateInputStream = new FileInputStream(certificateFile)) {
                        var certificate = (X509Certificate) certificateFactory.generateCertificate(certificateInputStream);
                        var notAfter = certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        if (notAfter.isBefore(expiry)) {
                            addError(FAILURE_MESSAGE.formatted(certificateFile.getName(), notAfter.format(ISO_DATE), monthsUntilExpiration, today.format(ISO_DATE)));
                        }
                    }
                }
            }
        } catch (IOException|CertificateException e) {
            throw new MojoFailureException("Unable to check certificates " + e.getLocalizedMessage(), e);
        }
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
