package se.sundsvall.dept44.configuration;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static se.sundsvall.dept44.configuration.Constants.APPLICATION_YAML;
import static se.sundsvall.dept44.configuration.Constants.APPLICATION_YML;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.swagger.v3.oas.annotations.Operation;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.util.ResourceUtils;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebConfiguration implements WebMvcConfigurer {

	private final YAMLMapper yamlMapper;

	WebConfiguration(final YAMLMapper yamlMapper) {
		this.yamlMapper = yamlMapper;
	}

	@Bean
	FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
		var registration = new FilterRegistrationBean<>(new RequestIdFilter());
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

	@Override
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		var yamlConverter = new MappingJackson2HttpMessageConverter(yamlMapper);
		yamlConverter.setSupportedMediaTypes(List.of(APPLICATION_YAML, APPLICATION_YML));
		converters.add(yamlConverter);

		// Remove null valued attributes from response JSON
		converters.stream()
			.filter(MappingJackson2HttpMessageConverter.class::isInstance)
			.map(MappingJackson2HttpMessageConverter.class::cast)
			.forEach(c -> c.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL));
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
			return openApiWebMvcResource.openapiYaml(request, apiDocsPath, Locale.getDefault());
		}
	}

	static class RequestIdFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
			var requestId = request.getHeader(RequestId.HEADER_NAME);

			RequestId.init(requestId);
			response.setHeader(RequestId.HEADER_NAME, RequestId.get());

			try {
				chain.doFilter(request, response);
			} finally {
				RequestId.reset();
			}
		}
	}
}
