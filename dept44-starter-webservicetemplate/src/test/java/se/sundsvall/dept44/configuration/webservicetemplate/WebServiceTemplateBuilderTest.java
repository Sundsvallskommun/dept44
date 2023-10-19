package se.sundsvall.dept44.configuration.webservicetemplate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.ARRAY;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.http.HttpComponents5ClientFactory;
import org.zalando.logbook.Logbook;

import se.sundsvall.dept44.configuration.webservicetemplate.exception.WebServiceTemplateException;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.DefaultFaultInterceptor;
import se.sundsvall.dept44.support.BasicAuthentication;

@ExtendWith(MockitoExtension.class)
class WebServiceTemplateBuilderTest {

	@Mock
	private ClientInterceptor clientInterceptorMock;

	@Mock
	private Logbook logbookMock;

	@Mock
	private WebServiceMessageFactory webServiceMessageFactoryMock;

	@Test
	void testCreate() {
		assertThat(WebServiceTemplateBuilder.create()).isNotNull();
	}

	@Test
	void testCreateWithBothKeyStoreFileLocationAndKeyStoreDataSet() {
		assertThatExceptionOfType(WebServiceTemplateException.class)
			.isThrownBy(() -> WebServiceTemplateBuilder.create()
				.withKeyStoreFileLocation("dummyLocation")
				.withKeyStoreData("dummy".getBytes())
				.build())
			.withMessage("Only one of 'keyStoreFileLocation' and 'keyStoreData' may be set");
	}

	@Test
	void testWithPattern() {
		// Setup variables
		var baseUrl = "baseUrl";
		var userName = "userName";
		var password = "password";
		var connectTimeout = Duration.ofSeconds(5);
		var keyStoreFileLocation = "keyStoreFileLocation";
		var keyStoreData = "keyStoreData".getBytes(UTF_8);
		var keyStorePassword = "keyStorePassword";
		var package1 = "package1";
		var package2 = "package2";
		var package3 = "package3";
		var readTimeout = Duration.ofSeconds(10);

		// Create instance
		WebServiceTemplateBuilder builder = WebServiceTemplateBuilder.create()
			.withBaseUrl(baseUrl)
			.withBasicAuthentication(userName, password)
			.withClientInterceptor(clientInterceptorMock)
			.withConnectTimeout(connectTimeout)
			.withKeyStoreFileLocation(keyStoreFileLocation)
			.withKeyStoreData(keyStoreData)
			.withKeyStorePassword(keyStorePassword)
			.withLogbook(logbookMock)
			.withPackagesToScan(List.of(package1, package2))
			.withPackageToScan(package3)
			.withReadTimeout(readTimeout)
			.withWebServiceMessageFactory(webServiceMessageFactoryMock);

		// Do assertions
		assertThat(builder)
			.hasFieldOrPropertyWithValue("baseUrl", baseUrl)
			.hasFieldOrPropertyWithValue("basicAuthentication", new BasicAuthentication(userName, password))
			.hasFieldOrPropertyWithValue("clientInterceptors", Set.of(clientInterceptorMock))
			.hasFieldOrPropertyWithValue("connectTimeout", connectTimeout)
			.hasFieldOrPropertyWithValue("keyStoreFileLocation", keyStoreFileLocation)
			.hasFieldOrPropertyWithValue("keyStoreData", keyStoreData)
			.hasFieldOrPropertyWithValue("keyStorePassword", keyStorePassword)
			.hasFieldOrPropertyWithValue("logbook", logbookMock)
			.hasFieldOrPropertyWithValue("packagesToScan", Set.of(package1, package2, package3))
			.hasFieldOrPropertyWithValue("readTimeout", readTimeout)
			.hasFieldOrPropertyWithValue("webServiceMessageFactory", webServiceMessageFactoryMock);
	}

	@Test
	void testClientInterceptorLogic() {
		// Create instance
		WebServiceTemplateBuilder builder = WebServiceTemplateBuilder.create()
			.withClientInterceptor(clientInterceptorMock)
			.withClientInterceptor(clientInterceptorMock);

		// Do assertions
		assertThat(builder)
			.hasFieldOrPropertyWithValue("clientInterceptors", Set.of(clientInterceptorMock));
	}

	@Test
	void testPackagesToScanLogic() {
		// Setup variables
		var package1 = "package1";
		var package2 = "package2";
		var package3 = "package3";

		// Create instance
		WebServiceTemplateBuilder builder = WebServiceTemplateBuilder.create()
			.withPackagesToScan(List.of(package1, package2))
			.withPackagesToScan(List.of(package1, package3));

		// Do assertions
		assertThat(builder)
			.hasFieldOrPropertyWithValue("packagesToScan", Set.of(package1, package2, package3));
	}

	@Test
	void testPackageToScanLogic() {
		// Setup variables
		var package1 = "package1";
		var package2 = "package2";

		// Create instance
		WebServiceTemplateBuilder builder = WebServiceTemplateBuilder.create()
			.withPackageToScan(package1)
			.withPackageToScan(package1)
			.withPackageToScan(package2);

		// Do assertions
		assertThat(builder)
			.hasFieldOrPropertyWithValue("packagesToScan", Set.of(package1, package2));
	}

	@Test
	void testEmptyWebServiceTemplate() {
		// Create instance
		WebServiceTemplate template = WebServiceTemplateBuilder.create().build();

		// Do assertions
		assertThat(template).isNotNull();
		assertThat(template.getInterceptors()).hasOnlyElementsOfType(DefaultFaultInterceptor.class);
	}

	@Test
	void testWebServiceTemplateWithInterceptor() {
		// Create instance
		WebServiceTemplate template = WebServiceTemplateBuilder.create()
			.withClientInterceptor(clientInterceptorMock)
			.withLogbook(logbookMock)
			.build();

		// Do assertions
		assertThat(template).isNotNull();
		assertThat(template.getInterceptors()).containsExactly(clientInterceptorMock);
	}

	@Test
	void testWebServiceTemplateWithPackagesToScan() {
		// Setup variables
		var package1 = "package1";
		var package2 = "package2";
		var package3 = "package3";

		// Create instance
		WebServiceTemplate template = WebServiceTemplateBuilder.create().withPackagesToScan(List.of(package1, package2)).withPackageToScan(package3).build();

		// Do assertions
		assertThat(template).isNotNull();
		assertThat(template.getMarshaller()).isNotNull().isInstanceOf(Jaxb2Marshaller.class);
		assertThat(template.getUnmarshaller()).isNotNull().isInstanceOf(Jaxb2Marshaller.class);
		assertThat(template.getMarshaller()).extracting("packagesToScan").asInstanceOf(ARRAY).containsExactlyInAnyOrder(package1, package2, package3);
		assertThat(template.getUnmarshaller()).extracting("packagesToScan").asInstanceOf(ARRAY).containsExactlyInAnyOrder(package1, package2, package3);
	}

	@Test
	void testWebserviceTemplateWithBasicAuth() {
		// Setup variables
		var userName = "userName";
		var password = "password";

		// Create instance
		WebServiceTemplate template = WebServiceTemplateBuilder.create().withBasicAuthentication(userName, password).build();

		// Do assertions
		assertThat(template).isNotNull()
			.extracting("messageSenders").asInstanceOf(ARRAY).hasSize(1)
			.extracting("clientFactory", HttpComponents5ClientFactory.class)
			.extracting("credentials", UsernamePasswordCredentials.class).containsExactly(new UsernamePasswordCredentials(userName, password.toCharArray()));
	}

	@Test
	void testSSLClientWithKeyStoreFileLocation() {
		// Setup variables
		var keyStoreFileLocation = "classpath:dummy-keystore.jks";
		var keyStorePassword = "password";

		// Create instance
		var template = WebServiceTemplateBuilder.create()
			.withKeyStoreFileLocation(keyStoreFileLocation)
			.withKeyStorePassword(keyStorePassword)
			.build();

		// Do assertions
		assertThat(template).isNotNull();
	}

	@Test
	void testSSLClientWithKeyStoreFileContent() throws IOException {
		// Setup variables
		try (var in = getClass().getResourceAsStream("/dummy-keystore.jks")) {
			assert in != null;

			var keyStoreFileContent = in.readAllBytes();
			var keyStorePassword = "password";

			// Create instance
			var template = WebServiceTemplateBuilder.create()
				.withKeyStoreData(keyStoreFileContent)
				.withKeyStorePassword(keyStorePassword)
				.build();

			// Do assertions
			assertThat(template).isNotNull();
		}
	}
}
