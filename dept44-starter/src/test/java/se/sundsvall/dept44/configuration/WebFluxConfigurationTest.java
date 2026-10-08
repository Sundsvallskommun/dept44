package se.sundsvall.dept44.configuration;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebFluxConfigurationTest {

	@Nested
	@SpringBootTest(classes = WebFluxConfiguration.class, properties = "spring.main.web-application-type=reactive")
	class WebFluxConfigurationEnabledTest {

		@Autowired
		private WebFluxConfiguration.RequestIdHandlerFilterFunction requestIdHandlerFilterFunction;

		@Autowired
		private WebFluxConfiguration.DisableBrowserCacheFilterFunction disableBrowserCacheFilterFunction;

		@Test
		void requestIdHandlerFilterFunctionIsAutowired() {
			assertThat(requestIdHandlerFilterFunction).isNotNull();
		}

		@Test
		void disableBrowserCacheFilterFunctionIsAutowired() {
			assertThat(disableBrowserCacheFilterFunction).isNotNull();
		}
	}

	@Nested
	@SpringBootTest(classes = WebFluxConfiguration.class)
	class WebFluxConfigurationDisabledTest {

		@Autowired(required = false)
		private WebFluxConfiguration.RequestIdHandlerFilterFunction requestIdHandlerFilterFunction;

		@Autowired(required = false)
		private WebFluxConfiguration.DisableBrowserCacheFilterFunction disableBrowserCacheFilterFunction;

		@Test
		void requestIdHandlerFilterFunctionIsNotAutowired() {
			assertThat(requestIdHandlerFilterFunction).isNull();
		}

		@Test
		void disableBrowserCacheFilterFunctionIsNotAutowired() {
			assertThat(disableBrowserCacheFilterFunction).isNull();
		}
	}

	@Nested
	@SpringBootTest(classes = WebFluxConfiguration.class, properties = "spring.main.web-application-type=reactive")
	class RequestIdHandlerFilterFunctionTest {

		@Autowired
		private WebFluxConfiguration.RequestIdHandlerFilterFunction requestIdHandlerFilterFunction;

		@Test
		void requestIdHandlerFilterFunctionFilter() {
			final var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").header(RequestId.HEADER_NAME, " requestId "));
			final var seenInContext = new AtomicReference<String>();
			final WebFilterChain chain = filteredExchange -> Mono.deferContextual(context -> {
				seenInContext.set(context.get(RequestId.CONTEXT_KEY));
				return Mono.empty();
			});

			requestIdHandlerFilterFunction.filter(exchange, chain).block();

			assertThat(exchange.getResponse().getHeaders().get(RequestId.HEADER_NAME)).containsExactly("requestId");
			assertThat(seenInContext.get()).isEqualTo("requestId");
			assertThat(RequestId.get()).isNull();
		}

		@Test
		void requestsHandledOnTheSameThreadGetTheirOwnRequestIds() {
			final var first = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
			final var second = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
			final WebFilterChain neverCompletes = filteredExchange -> Mono.never();

			// The first request is still in progress when the second one arrives
			requestIdHandlerFilterFunction.filter(first, neverCompletes).subscribe();
			requestIdHandlerFilterFunction.filter(second, neverCompletes).subscribe();

			final var firstId = first.getResponse().getHeaders().getFirst(RequestId.HEADER_NAME);
			final var secondId = second.getResponse().getHeaders().getFirst(RequestId.HEADER_NAME);
			assertThat(firstId).isNotBlank();
			assertThat(secondId).isNotBlank().isNotEqualTo(firstId);
		}
	}

	@Nested
	@SpringBootTest(classes = WebFluxConfiguration.class, properties = "spring.main.web-application-type=reactive")
	class DisableBrowserCacheFilterFunctionTest {

		@Mock
		private ServerWebExchange serverWebExchangeMock;

		@Mock
		private ServerHttpResponse serverHttpResponseMock;

		@Mock
		private HttpHeaders httpHeadersMock;

		@Mock
		private WebFilterChain webFilterChainMock;

		@Mock
		private Mono<Void> monoMock;

		@Autowired
		private WebFluxConfiguration.DisableBrowserCacheFilterFunction disableBrowserCacheFilterFunction;

		@Test
		void requestIdHandlerFilterFunctionFilter() {
			when(serverWebExchangeMock.getResponse()).thenReturn(serverHttpResponseMock);
			when(serverHttpResponseMock.getHeaders()).thenReturn(httpHeadersMock);
			doNothing().when(httpHeadersMock).setCacheControl("no-store");
			doNothing().when(httpHeadersMock).setExpires(0L);
			doNothing().when(httpHeadersMock).setPragma("no-cache");
			when(webFilterChainMock.filter(serverWebExchangeMock)).thenReturn(monoMock);

			disableBrowserCacheFilterFunction.filter(serverWebExchangeMock, webFilterChainMock);

			verify(serverHttpResponseMock).getHeaders();
			verify(httpHeadersMock).setCacheControl("no-store");
			verify(httpHeadersMock).setExpires(0L);
			verify(httpHeadersMock).setPragma("no-cache");
			verify(webFilterChainMock).filter(serverWebExchangeMock);
			verifyNoMoreInteractions(webFilterChainMock, serverHttpResponseMock);
		}
	}
}
