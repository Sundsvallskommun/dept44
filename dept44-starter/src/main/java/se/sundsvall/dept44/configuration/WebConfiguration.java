package se.sundsvall.dept44.configuration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.yaml.JacksonYamlHttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.dept44.util.ResourceUtils;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.http.MediaType.APPLICATION_YAML_VALUE;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static se.sundsvall.dept44.configuration.Constants.APPLICATION_YAML;
import static se.sundsvall.dept44.configuration.Constants.APPLICATION_YML;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebConfiguration implements WebMvcConfigurer {

	/** The name of the municipality id path variable, also used as its MDC key. */
	private static final String MUNICIPALITY_ID = "municipalityId";

	private final int municipalityIdUriIndex;
	private final List<String> allowedIds;

	WebConfiguration(
		@Value("${mdc.municipalityId.uriIndex:1}") final int municipalityIdUriIndex,
		@Value("${municipality.allowed-ids:}") final List<String> allowedIds) {
		this.municipalityIdUriIndex = municipalityIdUriIndex;
		this.allowedIds = allowedIds;
	}

	@Bean
	FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
		final var registration = new FilterRegistrationBean<>(new RequestIdFilter());
		registration.addUrlPatterns("/*");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	FilterRegistrationBean<IdentifierFilter> identifierFilterRegistration() {
		final var registration = new FilterRegistrationBean<>(new IdentifierFilter());
		registration.addUrlPatterns("/*");
		registration.setOrder(1);
		return registration;
	}

	@Bean
	FilterRegistrationBean<DisableBrowserCacheFilter> disableBrowserCacheFilterRegistration() {
		final var registration = new FilterRegistrationBean<>(new DisableBrowserCacheFilter());
		registration.addUrlPatterns("/*");
		registration.setOrder(2);
		return registration;
	}

	@Bean
	@ConditionalOnProperty(name = "mdc.municipalityId.enabled", havingValue = "true")
	FilterRegistrationBean<MunicipalityIdFilter> municipalityIdFilterRegistration() {
		final var registration = new FilterRegistrationBean<>(new MunicipalityIdFilter(municipalityIdUriIndex));
		registration.addUrlPatterns("/*");
		registration.setOrder(1);
		return registration;
	}

	@Override
	public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
		configurer
			.favorParameter(false)
			.ignoreAcceptHeader(false)
			.defaultContentType(APPLICATION_JSON, APPLICATION_PROBLEM_JSON, APPLICATION_XML, APPLICATION_YAML, APPLICATION_YML, APPLICATION_OCTET_STREAM, TEXT_HTML, TEXT_PLAIN)
			.mediaType(APPLICATION_JSON.getSubtype(), APPLICATION_JSON)
			.mediaType(APPLICATION_PROBLEM_JSON.getSubtype(), APPLICATION_PROBLEM_JSON)
			.mediaType(APPLICATION_YAML.getSubtype(), APPLICATION_YAML)
			.mediaType(APPLICATION_YML.getSubtype(), APPLICATION_YML)
			.mediaType(APPLICATION_XML.getSubtype(), APPLICATION_XML)
			.mediaType(APPLICATION_OCTET_STREAM.getSubtype(), APPLICATION_OCTET_STREAM)
			.mediaType(TEXT_HTML.getSubtype(), TEXT_HTML)
			.mediaType(TEXT_PLAIN.getSubtype(), TEXT_PLAIN);
	}

	@Bean
	JacksonYamlHttpMessageConverter jacksonYamlHttpMessageConverter() {
		final var builder = YAMLMapper.builder()
			.changeDefaultPropertyInclusion(handler -> handler
				.withValueInclusion(NON_NULL)
				.withContentInclusion(NON_NULL));
		final var yamlConverter = new JacksonYamlHttpMessageConverter(builder);
		yamlConverter.setSupportedMediaTypes(List.of(APPLICATION_YAML, APPLICATION_YML));
		return yamlConverter;
	}

	@Override
	public void addInterceptors(final InterceptorRegistry registry) {
		// Check that the municipality ID is allowed, for every handler whose path has a {municipalityId} variable
		registry.addInterceptor(new MunicipalityIdInterceptor(allowedIds));
	}

	/**
	 * Rejects a request for a municipality that is not in {@code municipality.allowed-ids}, when that list is set. The
	 * municipality is the {@code {municipalityId}} variable of the handler's path pattern; a request whose handler has no
	 * such variable (such as {@code /api-docs} or static resources) is not about a municipality and is let through.
	 */
	static class MunicipalityIdInterceptor implements HandlerInterceptor {

		private final List<String> allowedIds;

		MunicipalityIdInterceptor(final List<String> allowedIds) {
			this.allowedIds = allowedIds;
		}

		@Override
		public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object notUsed) {
			if (!allowedIds.isEmpty()) {
				final var municipalityId = municipalityId(request);

				if (municipalityId != null && !allowedIds.contains(municipalityId)) {
					throw Problem.builder().withStatus(NOT_IMPLEMENTED).withDetail("Not implemented for municipalityId: " + municipalityId).build();
				}
			}

			return true;
		}

		private static String municipalityId(final HttpServletRequest request) {
			if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof final Map<?, ?> variables) {
				return variables.get(MUNICIPALITY_ID) instanceof final String value ? value : null;
			}
			return null;
		}
	}

	@RestController
	@RequestMapping("/")
	@ConditionalOnProperty(name = "openapi.enabled", havingValue = "true", matchIfMissing = true)
	static class IndexPageController {

		private final String apiDocsPath;
		private final String template;
		private final OpenApiWebMvcResource openApiWebMvcResource;

		IndexPageController(@Value("${springdoc.api-docs.path}") final String apiDocsPath,
			final OpenApiWebMvcResource openApiWebMvcResource,
			@Value("classpath:templates/index.html.template") final Resource templateResource) {
			this.apiDocsPath = apiDocsPath;
			this.openApiWebMvcResource = openApiWebMvcResource;

			template = ResourceUtils.asString(templateResource).replace("@API_DOC_URI@", apiDocsPath).replace("@API_DOC_URI_RELATIVE@", apiDocsPath.replaceFirst("/", ""));
		}

		@Operation(hidden = true)
		@GetMapping(produces = MediaType.TEXT_HTML_VALUE)
		String showIndexPage() {
			return template;
		}

		@Operation(tags = "API",
			summary = "OpenAPI",
			responses = @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = APPLICATION_YAML_VALUE, schema = @Schema(type = "string"))))
		@GetMapping(value = "${springdoc.api-docs.path}", produces = APPLICATION_YAML_VALUE)
		void getApiDocs(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
			response.setContentType(APPLICATION_YAML_VALUE);
			response.getOutputStream().write(openApiWebMvcResource.openapiYaml(request, apiDocsPath, Locale.getDefault()));
		}
	}

	static class RequestIdFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
			final var requestId = request.getHeader(RequestId.HEADER_NAME);

			RequestId.init(requestId);
			response.setHeader(RequestId.HEADER_NAME, RequestId.get());

			try {
				chain.doFilter(request, response);
			} finally {
				RequestId.reset();
			}
		}
	}

	static class IdentifierFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws ServletException, IOException {
			final var identifierString = request.getHeader(Identifier.HEADER_NAME);

			try {
				Identifier.set(Identifier.parse(identifierString));
				chain.doFilter(request, response);
			} finally {
				Identifier.remove();
			}
		}
	}

	static class DisableBrowserCacheFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
			// Set, not added: Spring Security has already written its own cache headers, which these replace
			response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
			response.setIntHeader(HttpHeaders.EXPIRES, 0);
			response.setHeader(HttpHeaders.PRAGMA, "no-cache");

			chain.doFilter(request, response);
		}
	}

	static class MunicipalityIdFilter extends OncePerRequestFilter {

		private final int municipalityIdUriIndex;

		public MunicipalityIdFilter(final int municipalityIdUriIndex) {
			this.municipalityIdUriIndex = municipalityIdUriIndex;
		}

		@Override
		protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
			final var pathParams = request.getRequestURI().split("/");
			if (pathParams.length > municipalityIdUriIndex) {
				MDC.put(MUNICIPALITY_ID, pathParams[municipalityIdUriIndex]);
			}

			try {
				chain.doFilter(request, response);
			} finally {
				MDC.remove(MUNICIPALITY_ID);
			}
		}
	}
}
