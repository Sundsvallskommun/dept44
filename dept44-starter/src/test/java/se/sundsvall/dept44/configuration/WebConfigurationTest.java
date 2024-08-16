package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import se.sundsvall.dept44.requestid.RequestId;

class WebConfigurationTest {

	@Nested
	@SpringBootTest(classes = WebConfiguration.class, properties = "mdc.municipalityId.enabled=true")
	class WebConfigurationEnabledTest {

		@MockBean
		private YAMLMapper mockYamlMapper;

		@MockBean
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
	}

	@Nested
	@SpringBootTest(classes = WebConfiguration.class, properties = "openapi.enabled=false")
	class WebConfigurationWithIndexPageControllerDisabledTest {

		@MockBean
		private YAMLMapper mockYamlMapper;

		@MockBean
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

		@MockBean
		private YAMLMapper mockYamlMapper;

		@MockBean
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

		@MockBean
		private YAMLMapper mockYamlMapper;

		@MockBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Mock
		HttpServletRequest httpServletRequestMock;

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

		@MockBean
		private YAMLMapper mockYamlMapper;

		@MockBean
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
	class DisableBrowserCacheFilterTest {

		@MockBean
		private YAMLMapper mockYamlMapper;

		@MockBean
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
	@SpringBootTest(classes = WebConfiguration.class, properties = { "mdc.municipalityId.enabled=true" })
	class MunicipalityIdFilterTest {

		@MockBean
		private YAMLMapper mockYamlMapper;

		@MockBean
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

			try (MockedStatic<MDC> mdc = Mockito.mockStatic(MDC.class)) {
				municipalityIdFilter.doFilterInternal(httpServletRequestMock, httpServletResponseMock, filterChainMock);
				mdc.verify(() -> MDC.put("municipalityId", "2281"));
				mdc.verify(() -> MDC.remove("municipalityId"));
			}

			verify(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);
			verify(httpServletRequestMock).getRequestURI();
		}
	}
}
