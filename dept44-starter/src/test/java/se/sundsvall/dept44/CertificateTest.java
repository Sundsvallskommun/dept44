package se.sundsvall.dept44;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;

// TODO: Remove this when the certificates are replaced.
@Disabled
class CertificateTest {

	private static final String PATH = "internal-truststore/";
	private static final String CERTIFICATE_SUFFIX = ".cer";
	private static final String FAIL_MESSAGE = "Certificate '%s' expiration date is less than %s months from now (%s) and needs to be replaced";
	private static final int MONTHS_UNTIL_EXPIRATION = 1;

	private static CertificateFactory FACTORY;
	private static Calendar CALENDAR = Calendar.getInstance();
	private static SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@BeforeAll
	static void initialize() throws Exception {
		FACTORY = CertificateFactory.getInstance("X509");
		CALENDAR.set(Calendar.MONTH, CALENDAR.get(Calendar.MONTH) + MONTHS_UNTIL_EXPIRATION);
	}

	@ParameterizedTest
	@MethodSource("certificateProvider")
	void verifyCertificateExpiryDate(final String certificate) throws Exception {

		final var stream = new ClassPathResource(PATH + certificate).getInputStream();
		final var cert = (X509Certificate) FACTORY.generateCertificate(stream);

		assertThat(cert.getNotAfter())
			.withFailMessage(FAIL_MESSAGE, certificate, MONTHS_UNTIL_EXPIRATION, SDF.format(cert.getNotAfter()))
			.isAfterOrEqualTo(CALENDAR.getTime());
	}

	private static Stream<Arguments> certificateProvider() throws IOException {
		return Stream.of(ResourceUtils.getFile("classpath:" + PATH).listFiles())
			.filter(file -> file.getName().endsWith(CERTIFICATE_SUFFIX))
			.map(File::getName)
			.map(Arguments::of);
	}
}
