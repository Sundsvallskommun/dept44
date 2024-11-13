package se.sundsvall.dept44.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Collections;
import java.util.Optional;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "openapi.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfiguration {

	private static final String SECURITY_SCHEME_OAUTH2 = "OAuth2";

	private final OpenApiProperties properties;

	OpenApiConfiguration(final OpenApiProperties properties) {
		this.properties = properties;
	}

	@Bean("dept44OpenApi")
	OpenAPI customOpenAPI(
		@Autowired(
			required = false) @Qualifier("dept44Oauth2SecurityScheme") final SecurityScheme oauth2SecurityScheme) {
		return new OpenAPI()
			.servers(properties.getServers().stream()
				.map(server -> new Server()
					.url(server.getUrl())
					.description(server.getDescription()))
				.toList())
			.extensions(properties.getExtensions())
			.components(new Components()
				.securitySchemes(Optional.ofNullable(oauth2SecurityScheme)
					.map(scheme -> Collections.singletonMap(SECURITY_SCHEME_OAUTH2, oauth2SecurityScheme))
					.orElse(Collections.emptyMap())))
			.info(new Info()
				.title(properties.getTitle())
				.description(properties.getDescription())
				.version(properties.getVersion())
				.license(new License()
					.name(properties.getLicense().getName())
					.url(properties.getLicense().getUrl()))
				.contact(new Contact()
					.name(properties.getContact().getName())
					.url(properties.getContact().getUrl())
					.email(properties.getContact().getEmail())));
	}

	@Bean("dept44Oauth2SecurityScheme")
	@ConditionalOnProperty(prefix = "openapi.security-scheme.oauth2.flow", name = "tokenUrl")
	SecurityScheme oauth2SecurityScheme(@Qualifier("dept44OauthFlow") final OAuthFlow oauthFlow) {
		return new SecurityScheme()
			.type(SecurityScheme.Type.OAUTH2)
			.flows(new OAuthFlows().clientCredentials(oauthFlow));
	}

	@Bean("dept44OauthFlow")
	@ConditionalOnBean(name = "dept44Oauth2SecurityScheme")
	@ConfigurationProperties(value = "openapi.security-scheme.oauth2.flow")
	OAuthFlow oauthFlow() {
		return new OAuthFlow();
	}

	@Bean
	OpenApiCustomizer apiDocsOpenApiCustomizer() {
		return openApi -> Optional.ofNullable(openApi.getPaths().get("/api-docs"))
			.flatMap(openApiPath -> Optional.ofNullable(openApiPath.getGet())).ifPresent(this::extendOperation);
	}

	void extendOperation(Operation operation) {
		operation.addExtension("x-auth-type", "None");
		operation.addExtension("x-throttling-tier", "Unlimited");
		operation.addExtension("x-wso2-mutual-ssl", "Optional");
	}
}
