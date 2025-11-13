package se.sundsvall.dept44;

import static java.util.Calendar.MONTH;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

class CertificateTest {

	private static final String CERTIFICATE_PATH = "internal-truststore/";
	private static final String CERTIFICATE_SUFFIX_PATTERN = "^.*(\\.cer|\\.crt)$";
	private static final String FAIL_MESSAGE = "Certificate '%s' expiration date is less than %s months from now (%s) and needs to be replaced";
	private static final int MONTHS_UNTIL_EXPIRATION = 1;

	private CertificateFactory certificateFactory;
	private final Calendar calendar = Calendar.getInstance();
	private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@BeforeEach
	void initialize() throws Exception {
		certificateFactory = CertificateFactory.getInstance("X509");
		calendar.set(MONTH, calendar.get(MONTH) + MONTHS_UNTIL_EXPIRATION);
	}

	@ParameterizedTest
	@MethodSource("certificateProvider")
	void verifyCertificateExpiryDate(final String certificate) throws Exception {
		final var stream = new ClassPathResource(CERTIFICATE_PATH + certificate).getInputStream();
		final var cert = (X509Certificate) certificateFactory.generateCertificate(stream);

		assertThat(cert.getNotAfter())
			.withFailMessage(FAIL_MESSAGE, certificate, MONTHS_UNTIL_EXPIRATION, sdf.format(cert.getNotAfter()))
			.isAfterOrEqualTo(calendar.getTime());
	}

	private static Stream<Arguments> certificateProvider() throws IOException {
		return Stream.of(new ClassPathResource(CERTIFICATE_PATH).getFile().listFiles())
			.map(File::getName)
			.filter(name -> name.matches(CERTIFICATE_SUFFIX_PATTERN))
			.map(Arguments::of);
	}
}
