package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = OpenApiConfiguration.class)
@ActiveProfiles("junit")
class OpenApiConfigurationTest {

	@MockitoBean
	private Operation operationMock;

	@Autowired
	private OpenApiProperties openApiProperties;

	@Autowired
	private SecurityScheme securityScheme;

	@Autowired
	private OpenApiConfiguration openApiConfiguration;

	@Test
	void customApi() {
		final var openApi = openApiConfiguration.customOpenAPI(securityScheme);
		assertThat(openApi).isNotNull();
	}

	@Test
	void properties() {
		assertThat(openApiProperties).isNotNull();
		assertThat(openApiProperties.getName()).isEqualTo("test-name");
		assertThat(openApiProperties.getTitle()).isEqualTo("test-title");
		assertThat(openApiProperties.getDescription()).isEqualTo("test-description");
		assertThat(openApiProperties.getVersion()).isEqualTo("test-version");
		assertThat(openApiProperties.getLicense().getName()).isEqualTo("license-test-name");
		assertThat(openApiProperties.getLicense().getUrl()).isEqualTo("license-test-url");
		assertThat(openApiProperties.getServers().get(0).getUrl()).isEqualTo("test-server-url");
		assertThat(openApiProperties.getServers().get(0).getDescription()).isEqualTo("test-server-description");
		assertThat(openApiProperties.getContact().getName()).isEqualTo("test-contact-name");
		assertThat(openApiProperties.getContact().getUrl()).isEqualTo("test-contact-url");
		assertThat(openApiProperties.getContact().getEmail()).isEqualTo("test-contact-email");
		assertThat(openApiProperties.getExtensions()).containsEntry("0", Map.of("test", "test-extension"));
	}

	@Test
	void apiDocsOpenApiCustomizer() {
		openApiConfiguration.extendOperation(operationMock);

		verify(operationMock).addExtension("x-auth-type", "None");
		verify(operationMock).addExtension("x-throttling-tier", "Unlimited");
		verify(operationMock).addExtension("x-wso2-mutual-ssl", "Optional");
	}
}
