package se.sundsvall.petinventory.apptest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import se.sundsvall.petinventory.Application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration test for the SSL health indicator, verifying that it correctly identifies expiring certificates and reports the appropriate health status and details.
 * As there are no integration-tests in the starter module we place this test in the petinventory module, which has the necessary dependencies and setup to run this test.
 */
@ActiveProfiles("it")
@WireMockAppTestSuite(files = "classpath:/PetInventoryIT/", classes = Application.class)
class SslHealthIndicatorIT extends AbstractAppTest {

	private static final Path KEYSTORE_PATH = Path.of("target/test-certs/expiring.p12");
	private static final String PASSWORD = "changeit";
	private static final String STORE_TYPE = "PKCS12";

	// Create a self-signed certificate that is expiring in 1 days, which is within the configured warning threshold of 30 days.
	static {
		try {
			Files.createDirectories(KEYSTORE_PATH.getParent());
			Files.deleteIfExists(KEYSTORE_PATH);

			final var process = new ProcessBuilder(
				"keytool",
				"-genkeypair",
				"-alias", "expiringcertificate",
				"-keyalg", "RSA",
				"-keysize", "2048",
				"-validity", "1",
				"-storetype", STORE_TYPE,
				"-keystore", KEYSTORE_PATH.toString(),
				"-storepass", PASSWORD,
				"-dname", "CN=localhost")
				.redirectErrorStream(true)
				.start();

			final var exitCode = process.waitFor();
			
			if (exitCode != 0) {
				final var output = new String(process.getInputStream().readAllBytes());
				throw new RuntimeException("keytool failed with exitCode: " + exitCode + ", output: " + output);
			}
		} catch (final Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@DynamicPropertySource
	static void registerProperties(final DynamicPropertyRegistry registry) {
		registry.add("spring.ssl.bundle.jks.expiring-test.keystore.location", () -> "file:" + KEYSTORE_PATH);
		registry.add("spring.ssl.bundle.jks.expiring-test.keystore.password", () -> PASSWORD);
		registry.add("spring.ssl.bundle.jks.expiring-test.keystore.type", () -> STORE_TYPE);
		registry.add("management.health.ssl.certificate-validity-warning-threshold", () -> "30d");
	}

	@Test
	void testHealthEndpointReportsOutOfServiceForExpiringCertificate() throws Exception {
		final var response = restTemplate.getForEntity("/actuator/health", String.class);
		final var mapper = JsonMapper.builder().build();
		final var root = mapper.readTree(response.getBody());
		
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(root.path("status").asString()).isEqualTo("RESTRICTED");

		// Get the expiring certificate chain details from the health endpoint response
		final var expiringChains = root.path("components").path("ssl").path("details").path("expiringChains");
		assertThat(expiringChains.isArray()).isTrue();

		// Find the entry for our expiring certificate using the alias
		final var expiringEntry = findByAlias(expiringChains, "expiringcertificate");
		assertThat(expiringEntry).isNotNull();

		// Extract the certificate details and verify that it is valid but expiring soon
		final var cert = expiringEntry.path("certificates").path(0);
		assertThat(cert.path("validity").path("status").asString()).isEqualTo("VALID");
		
		// Validity is set to expire in one day, so it should be before now + 2 days (to allow for some clock skew)
		assertThat(Instant.parse(cert.path("validityEnds").asString())).isBefore(Instant.now().plus(2, ChronoUnit.DAYS));
	}

	private JsonNode findByAlias(final JsonNode array, final String alias) {
		for (final var entry : array) {
			if (alias.equals(entry.path("alias").asString())) {
				return entry;
			}
		}
		return null;
	}
}
