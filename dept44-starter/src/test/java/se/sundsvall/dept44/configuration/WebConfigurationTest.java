package se.sundsvall.dept44.configuration;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import se.sundsvall.dept44.requestid.RequestId;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebConfigurationTest {

	@Nested
	@SpringBootTest(classes = WebConfiguration.class)
	class WebConfigurationEnabledTest {

		@MockBean
		private YAMLMapper mockYamlMapper;
		@MockBean
		private OpenApiWebMvcResource mockOpenApiWebMvcResource;

		@Autowired
		private FilterRegistrationBean<WebConfiguration.RequestIdFilter> requestIdFilterRegistration;
		@Autowired
		private WebConfiguration.IndexPageController indexPageController;
		@Autowired
		private WebConfiguration webConfiguration;

		@Test
		void test_requestIdFilterRegistration_isAutowired() {
			assertThat(requestIdFilterRegistration).isNotNull();
		}

		@Test
		void test_indexPageController_isAutowired() {
			assertThat(indexPageController).isNotNull();
		}

		@Test
		void test_configureContentNegotiation() {
			final var contentNegotiationConfigurer = new ContentNegotiationConfigurer(null);
			webConfiguration.configureContentNegotiation(contentNegotiationConfigurer);

			assertThat(contentNegotiationConfigurer).isNotNull();
			assertThat(contentNegotiationConfigurer).extracting("mediaTypes")
					.asInstanceOf(InstanceOfAssertFactories.map(String.class, MediaType.class))
					.hasSize(8);
			assertThat(contentNegotiationConfigurer).extracting("factory").extracting("favorParameter").isEqualTo(false);
		}

		@Test
		void test_extendMessageConverters() {
			List<HttpMessageConverter<?>> converterList = new ArrayList<>();
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
		@Autowired(required = false)
		private WebConfiguration.IndexPageController indexPageController;

		@Test
		void test_requestIdFilterRegistration_isAutowired() {
			assertThat(requestIdFilterRegistration).isNotNull();
		}

		@Test
		void test_indexPageController_isNotAutowired() {
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
		private WebConfiguration.IndexPageController indexPageController;

		@Test
		void test_requestIdFilterRegistration_isNotAutowired() {
			assertThat(requestIdFilterRegistration).isNull();
		}

		@Test
		void test_indexPageController_isNotAutowired() {
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
		void test_showIndexPage() {
			assertThat(indexPageController.showIndexPage()).isNotNull();
		}

		@Test
		void test_getApiDocs() throws Exception{
			final var yamlString = "yamlString";
			when(mockOpenApiWebMvcResource.openapiYaml(any(), anyString(), any())).thenReturn(yamlString);
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
		void test_requestIdHandlerFilterFunctionFilter() throws IOException, ServletException {
			final var requestId = "requestId";
			final var requestIdFilter = requestIdFilterRegistration.getFilter();

			when(httpServletRequestMock.getHeader(anyString())).thenReturn(requestId);
			doNothing().when(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);

			requestIdFilter.doFilterInternal(httpServletRequestMock, httpServletResponseMock, filterChainMock);

			verify(filterChainMock).doFilter(httpServletRequestMock, httpServletResponseMock);
			verify(httpServletRequestMock).getHeader(RequestId.HEADER_NAME);
		}
	}
}
