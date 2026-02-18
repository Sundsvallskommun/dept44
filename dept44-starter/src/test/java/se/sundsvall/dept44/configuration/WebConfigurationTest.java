package se.sundsvall.dept44.configuration;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.zalando.problem.ThrowableProblem;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.dept44.support.Identifier.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WebConfigurationTest {

	@Nested
	@SpringBootTest(classes = WebConfiguration.class, properties = "mdc.municipalityId.enabled=true")
	class WebConfigurationEnabledTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.RequestIdFilter> requestIdFilterRegistration;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.DisableBrowserCacheFilter> disableBrowserCacheFilterRegistration;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.MunicipalityIdFilter> municipalityIdFilterRegistration;

		@Autowired
		private WebConfiguration.IndexPageController indexPageController;

		@Autowired
		private WebConfiguration webConfiguration;

		@Test
		void requestIdFilterRegistrationIsAutowired() {
			assertThat(requestIdFilterRegistration).isNotNull();
		}

		@Test
		void disableBrowserCacheFilterRegistrationIsAutowired() {
			assertThat(disableBrowserCacheFilterRegistration).isNotNull();
		}

		@Test
		void municipalityIdFilterRegistrationIsAutowired() {
			assertThat(municipalityIdFilterRegistration).isNotNull();
		}

		@Test
		void indexPageControllerIsAutowired() {
			assertThat(indexPageController).isNotNull();
		}

		@Test
		void configureContentNegotiation() {
			final var contentNegotiationConfigurer = new ContentNegotiationConfigurer(null);
			webConfiguration.configureContentNegotiation(contentNegotiationConfigurer);

			assertThat(contentNegotiationConfigurer).isNotNull();
			assertThat(contentNegotiationConfigurer).extracting("mediaTypes")
				.asInstanceOf(InstanceOfAssertFactories.map(String.class, MediaType.class))
				.hasSize(8);
			assertThat(contentNegotiationConfigurer).extracting("factory").extracting("favorParameter").isEqualTo(false);
		}

		@Test
		void extendMessageConverters() {
			final List<HttpMessageConverter<?>> converterList = new ArrayList<>();
			webConfiguration.extendMessageConverters(converterList);

			assertThat(converterList).isNotNull().hasSize(1);
		}

		@Test
		void addInterceptors() {
			final var interceptorRegistry = new InterceptorRegistry();
			webConfiguration.addInterceptors(interceptorRegistry);

			assertThat(interceptorRegistry).isNotNull();
			assertThat(interceptorRegistry).extracting("registrations").asInstanceOf(InstanceOfAssertFactories.list(InterceptorRegistration.class)).hasSize(1);
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class, properties = "openapi.enabled=false")
	class WebConfigurationWithIndexPageControllerDisabledTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.RequestIdFilter> requestIdFilterRegistration;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.DisableBrowserCacheFilter> disableBrowserCacheFilterRegistration;

		@Autowired(required = false)
		private WebConfiguration.IndexPageController indexPageController;

		@Test
		void requestIdFilterRegistrationIsAutowired() {
			assertThat(requestIdFilterRegistration).isNotNull();
		}

		@Test
		void disableBrowserCacheFilterRegistrationIsAutowired() {
			assertThat(disableBrowserCacheFilterRegistration).isNotNull();
		}

		@Test
		void indexPageControllerIsNotAutowired() {
			assertThat(indexPageController).isNull();
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class, properties = "spring.main.web-application-type=reactive")
	class WebConfigurationDisabledTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Autowired(required = false)
		private FilterRegistrationBean<WebConfiguration.RequestIdFilter> requestIdFilterRegistration;

		@Autowired(required = false)
		private FilterRegistrationBean<WebConfiguration.DisableBrowserCacheFilter> disableBrowserCacheFilterRegistration;

		@Autowired(required = false)
		private WebConfiguration.IndexPageController indexPageController;

		@Test
		void requestIdFilterRegistrationIsNotAutowired() {
			assertThat(requestIdFilterRegistration).isNull();
		}

		@Test
		void disableBrowserCacheFilterRegistrationIsNotAutowired() {
			assertThat(disableBrowserCacheFilterRegistration).isNull();
		}

		@Test
		void indexPageControllerIsNotAutowired() {
			assertThat(indexPageController).isNull();
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class)
	class IndexPageControllerTest {

		@Mock
		private HttpServletRequest httpServletRequestMock;

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Autowired
		private WebConfiguration.IndexPageController indexPageController;

		@Test
		void showIndexPage() {
			assertThat(indexPageController.showIndexPage()).isNotNull();
		}

		@Test
		void getApiDocs() throws Exception {
			final var yamlString = "yamlString";
			when(mockOpenApiWebMvcResource.openapiYaml(any(), anyString(), any())).thenReturn(yamlString.getBytes());
			final var apiDocs = indexPageController.getApiDocs(httpServletRequestMock);
			assertThat(apiDocs).isNotNull().isEqualTo(yamlString);
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class)
	class RequestIdFilterTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Mock
		private HttpServletRequest httpServletRequestMock;

		@Mock
		private HttpServletResponse httpServletResponseMock;

		@Mock
		private FilterChain filterChainMock;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.RequestIdFilter> requestIdFilterRegistration;

		@Test
		void doFilterInternal() throws IOException, ServletException {
			final var requestId = "requestId";
			final var requestIdFilter = requestIdFilterRegistration.getFilter();

			when(httpServletRequestMock.getHeader(anyString())).thenReturn(requestId);
			doNothing().when(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);

			requestIdFilter.doFilterInternal(httpServletRequestMock, httpServletResponseMock, filterChainMock);

			verify(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);
			verify(httpServletRequestMock).getHeader(RequestId.HEADER_NAME);
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class)
	class IdentifierFilterTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Mock
		private HttpServletRequest httpServletRequestMock;

		@Mock
		private HttpServletResponse httpServletResponseMock;

		@Mock
		private FilterChain filterChainMock;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.IdentifierFilter> identifierFilterRegistration;

		@ParameterizedTest
		@MethodSource("argumentsProvider")
		void doFilterInternal(String headerName, String headerValue, Identifier expectedIdentifier) throws IOException, ServletException {
			// Arrange
			final var identifierFilter = identifierFilterRegistration.getFilter();

			when(httpServletRequestMock.getHeader(headerName)).thenReturn(headerValue);
			doNothing().when(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);

			assertThat(Identifier.get()).isNull();

			// Act
			identifierFilter.doFilterInternal(httpServletRequestMock, httpServletResponseMock, filterChainMock);

			// Assert
			assertThat(Identifier.get()).isNull();
			verify(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);
			verify(httpServletRequestMock).getHeader(headerName);
			verifyNoMoreInteractions(httpServletRequestMock);
		}

		static Stream<Arguments> argumentsProvider() {
			return Stream.of(
				Arguments.of("X-Sent-By", "joe01doe; type=adAccount", Identifier.create().withType(Type.AD_ACCOUNT).withTypeString("AD_ACCOUNT").withValue("joe01doe")),
				Arguments.of("X-Sent-By", "fc956c60-d6ea-4ce6-9d9c-d71f8ab91be9; type=partyId", Identifier.create().withType(Type.PARTY_ID).withTypeString("PARTY_ID").withValue("fc956c60-d6ea-4ce6-9d9c-d71f8ab91be9")),
				Arguments.of("X-Sent-By", "xyz123; type=customType", Identifier.create().withType(Type.CUSTOM).withTypeString("customType").withValue("xyz123")),
				Arguments.of("X-Sent-By", null, null));
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class)
	class DisableBrowserCacheFilterTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Mock
		private HttpServletRequest httpServletRequestMock;

		@Mock
		private HttpServletResponse httpServletResponseMock;

		@Mock
		private FilterChain filterChainMock;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.DisableBrowserCacheFilter> disableBrowserCacheFilterRegistration;

		@Test
		void doFilterInternal() throws IOException, ServletException {
			final var disableBrowserCacheFilter = disableBrowserCacheFilterRegistration.getFilter();

			doNothing().when(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);

			disableBrowserCacheFilter.doFilterInternal(httpServletRequestMock, httpServletResponseMock, filterChainMock);

			verify(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);
			verify(httpServletResponseMock).addHeader(HttpHeaders.CACHE_CONTROL, "no-store");
			verify(httpServletResponseMock).addIntHeader(HttpHeaders.EXPIRES, 0);
			verify(httpServletResponseMock).addHeader(HttpHeaders.PRAGMA, "no-cache");
			verifyNoMoreInteractions(filterChainMock, httpServletResponseMock);
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class, properties = {
		"mdc.municipalityId.enabled=true"
	})
	class MunicipalityIdFilterTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Mock
		private HttpServletRequest httpServletRequestMock;

		@Mock
		private HttpServletResponse httpServletResponseMock;

		@Mock
		private FilterChain filterChainMock;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.MunicipalityIdFilter> municipalityIdFilterRegistration;

		@Test
		void doFilterInternal() throws IOException, ServletException {

			final var municipalityIdFilter = municipalityIdFilterRegistration.getFilter();
			final var uri = "/2281/somepath/123";

			when(httpServletRequestMock.getRequestURI()).thenReturn(uri);
			doNothing().when(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);

			try (final MockedStatic<MDC> mdc = Mockito.mockStatic(MDC.class)) {
				municipalityIdFilter.doFilterInternal(httpServletRequestMock, httpServletResponseMock, filterChainMock);
				mdc.verify(() -> MDC.put("municipalityId", "2281"));
				mdc.verify(() -> MDC.remove("municipalityId"));
			}

			verify(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);
			verify(httpServletRequestMock).getRequestURI();
		}
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class)
	class MunicipalityIdInterceptorTest {

		@MockitoBean
		private YAMLMapper mockYamlMapper;

		@MockitoBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Mock
		private HttpServletRequest httpServletRequestMock;

		@Mock
		private HttpServletResponse httpServletResponseMock;

		private WebConfiguration.MunicipalityIdInterceptor municipalityIdInterceptor;

		static Stream<Arguments> argumentsProvider() {
			return Stream.of(
				Arguments.of("/%s/any/service", "2281", 1),
				Arguments.of("/any/%s/service", "2281", 2),
				Arguments.of("/any/service/%s", "2281", 3),
				Arguments.of("/%s/any/service", "2260", 1),
				Arguments.of("/any/%s/service", "2260", 2),
				Arguments.of("/any/service/%s", "2260", 3));
		}

		@ParameterizedTest
		@MethodSource("argumentsProvider")
		void preHandleWithAllowedIds(final String path, final String municipalityId, final int municipalityIdUriIndex) {
			final var object = new Object();
			municipalityIdInterceptor = new WebConfiguration.MunicipalityIdInterceptor(List.of(municipalityId), municipalityIdUriIndex);

			when(httpServletRequestMock.getRequestURI()).thenReturn(path.formatted(municipalityId));

			final var result = municipalityIdInterceptor.preHandle(httpServletRequestMock, httpServletResponseMock, object);

			assertThat(result).isTrue();
			assertThatNoException().isThrownBy(() -> municipalityIdInterceptor.preHandle(httpServletRequestMock, httpServletResponseMock, object));
		}

		@ParameterizedTest
		@MethodSource("argumentsProvider")
		void preHandleWithNotAllowedIds(final String path, final String municipalityId, final int municipalityIdUriIndex) {
			final var object = new Object();
			municipalityIdInterceptor = new WebConfiguration.MunicipalityIdInterceptor(List.of("1234, 3214"), municipalityIdUriIndex);

			when(httpServletRequestMock.getRequestURI()).thenReturn(path.formatted(municipalityId));

			assertThatThrownBy(() -> municipalityIdInterceptor.preHandle(httpServletRequestMock, httpServletResponseMock, object))
				.isInstanceOf(ThrowableProblem.class)
				.hasMessage("Not implemented for municipalityId: " + municipalityId);
		}

		@ParameterizedTest
		@MethodSource("argumentsProvider")
		void preHandleWithNoConfiguredMunicipalityIds(final String path, final String municipalityId, final int municipalityIdUriIndex) {
			final var object = new Object();
			municipalityIdInterceptor = new WebConfiguration.MunicipalityIdInterceptor(List.of(), municipalityIdUriIndex);

			when(httpServletRequestMock.getRequestURI()).thenReturn(path.formatted(municipalityId));

			final var result = municipalityIdInterceptor.preHandle(httpServletRequestMock, httpServletResponseMock, object);

			assertThat(result).isTrue();
			assertThatNoException().isThrownBy(() -> municipalityIdInterceptor.preHandle(httpServletRequestMock, httpServletResponseMock, object));
		}
	}
}
