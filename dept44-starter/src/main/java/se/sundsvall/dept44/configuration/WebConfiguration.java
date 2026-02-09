package se.sundsvall.dept44.configuration;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static se.sundsvall.dept44.configuration.Constants.APPLICATION_YAML;
import static se.sundsvall.dept44.configuration.Constants.APPLICATION_YML;
import static se.sundsvall.dept44.problem.Status.NOT_IMPLEMENTED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
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
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.dept44.util.ResourceUtils;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebConfiguration implements WebMvcConfigurer {

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
		final var municipalityIdInterceptor = new MunicipalityIdInterceptor(allowedIds, municipalityIdUriIndex);

		// Add an interceptor to check if the municipality ID is allowed.
		// "/{municipalityId}" - Matches all paths where the municipality placeholder is the whole path.
		// "/**/{municipalityId}" - Matches all paths where the municipality placeholder is the last part of the path.
		// "/{municipalityId}/**" - Matches all paths where the municipality placeholder is the beginning of the path.
		// "/**/{municipalityId}/**" - Matches all paths where the municipality placeholder is in the middle of the path.
		registry.addInterceptor(municipalityIdInterceptor)
			.addPathPatterns("/{municipalityId}", "/{municipalityId}/**", "/**/{municipalityId}", "/**/{municipalityId}/**");
	}

	static class MunicipalityIdInterceptor implements HandlerInterceptor {

		private final List<String> allowedIds;
		private final Integer municipalityIdUriIndex;

		public MunicipalityIdInterceptor(final List<String> allowedIds, final int municipalityIdUriIndex) {
			this.allowedIds = allowedIds;
			this.municipalityIdUriIndex = municipalityIdUriIndex;
		}

		@Override
		public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object notUsed) {
			if (allowedIds.isEmpty()) {
				return true;
			}

			// Extracts the request URI.
			final String path = request.getRequestURI();
			// Split the path into parts
			final String[] pathArray = path.split("/");

			if (allowedIds.contains(pathArray[municipalityIdUriIndex])) {
				return true;
			}
			throw Problem.builder().withStatus(NOT_IMPLEMENTED).withDetail("Not implemented for municipalityId: " + pathArray[municipalityIdUriIndex]).build();
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

		@Operation(tags = "API", summary = "OpenAPI")
		@GetMapping(value = "${springdoc.api-docs.path}", produces = "application/yaml")
		String getApiDocs(final HttpServletRequest request) throws JsonProcessingException {
			return new String(openApiWebMvcResource.openapiYaml(request, apiDocsPath, Locale.getDefault()), Charset.defaultCharset());
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
			response.addHeader(HttpHeaders.CACHE_CONTROL, "no-store");
			response.addIntHeader(HttpHeaders.EXPIRES, 0);
			response.addHeader(HttpHeaders.PRAGMA, "no-cache");

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
				MDC.put("municipalityId", pathParams[municipalityIdUriIndex]);
			}

			try {
				chain.doFilter(request, response);
			} finally {
				MDC.remove("municipalityId");
			}
		}
	}
}
