package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import se.sundsvall.dept44.requestid.RequestId;

class WebFluxConfigurationTest {

	@Nested
	@SpringBootTest(classes = WebFluxConfiguration.class, properties = "spring.main.web-application-type=reactive")
	class WebFluxConfigurationEnabledTest {

		@Autowired
		private WebFluxConfiguration.RequestIdHandlerFilterFunction requestIdHandlerFilterFunction;

		@Test
		void requestIdHandlerFilterFunctionIsAutowired() {
			assertThat(requestIdHandlerFilterFunction).isNotNull();
		}

	}

	@Nested
	@SpringBootTest(classes = WebFluxConfiguration.class)
	class WebFluxConfigurationDisabledTest {

		@Autowired(required = false)
		private WebFluxConfiguration.RequestIdHandlerFilterFunction requestIdHandlerFilterFunction;

		@Test
		void requestIdHandlerFilterFunctionIsNotAutowired() {
			assertThat(requestIdHandlerFilterFunction).isNull();
		}

	}

	@Nested
	@SpringBootTest(classes = WebFluxConfiguration.class, properties = "spring.main.web-application-type=reactive")
	class RequestIdHandlerFilterFunctionTest {

		@Mock
		private ServerWebExchange serverWebExchangeMock;

		@Mock
		private ServerHttpRequest serverHttpRequestMock;

		@Mock
		private ServerHttpResponse serverHttpResponseMock;

		@Mock
		private HttpHeaders httpHeadersMock;

		@Mock
		private WebFilterChain webFilterChainMock;

		@Mock
		private Mono<Void> monoMock;

		@Autowired
		private WebFluxConfiguration.RequestIdHandlerFilterFunction requestIdHandlerFilterFunction;

		@Test
		void requestIdHandlerFilterFunctionFilter() {
			final var requestId = "requestId";

			when(serverWebExchangeMock.getRequest()).thenReturn(serverHttpRequestMock);
			when(serverWebExchangeMock.getResponse()).thenReturn(serverHttpResponseMock);
			when(serverHttpRequestMock.getHeaders()).thenReturn(httpHeadersMock);
			when(serverHttpResponseMock.getHeaders()).thenReturn(httpHeadersMock);
			when(httpHeadersMock.getFirst(anyString())).thenReturn(requestId);
			when(webFilterChainMock.filter(serverWebExchangeMock)).thenReturn(monoMock);
			when(monoMock.then()).thenReturn(monoMock);
			when(monoMock.doFinally(any())).thenReturn(monoMock);

			requestIdHandlerFilterFunction.filter(serverWebExchangeMock, webFilterChainMock);

			// Because call to doFinally is mocked RequestId must be resettled for not disturbing other tests
			RequestId.reset();

			verify(httpHeadersMock).add(RequestId.HEADER_NAME, requestId);
			verify(webFilterChainMock).filter(serverWebExchangeMock);
			verify(monoMock).then();
			verify(monoMock).doFinally(any());
		}
	}
}
