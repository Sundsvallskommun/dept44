package se.sundsvall.dept44.configuration.webservicetemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.ARRAY;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.zalando.logbook.Logbook;

import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.DefaultFaultInterceptor;
import se.sundsvall.dept44.support.BasicAuthentication;

class WebServiceTemplateBuilderTest {

	@Mock
	private ClientInterceptor clientInterceptorMock;

	@Mock
	private Logbook logbookMock;

	@Mock
	private WebServiceMessageFactory webServiceMessageFactoryMock;

	@BeforeEach
	public void initMocks() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testCreate() {
		assertThat(WebServiceTemplateBuilder.create()).isNotNull();
	}

	@Test
	void testWithPattern() {
		// Setup variables
		var baseUrl = "baseUrl";
		var userName = "userName";
		var password = "password";
		var connectTimeout = Duration.ofSeconds(5);
		var keyStoreFileLocation = "keyStoreFileLocation";
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
			.extracting("credentials", UsernamePasswordCredentials.class).containsExactly(new UsernamePasswordCredentials(userName, password));
	}

	@Test
	void testSSLClient() {
		// Setup variables
		var keyStoreFileLocation = "classpath:dummy-keystore.jks";
		var keyStorePassword = "password";

		// Create instance
		WebServiceTemplate template = WebServiceTemplateBuilder.create().withKeyStoreFileLocation(keyStoreFileLocation).withKeyStorePassword(keyStorePassword).build();

		// Do assertions
		assertThat(template).isNotNull();
	}
}
