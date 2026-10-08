package se.sundsvall.dept44.configuration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import se.sundsvall.dept44.requestid.RequestId;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class WebFluxConfiguration {

	@Configuration
	static class WebFluxConfig implements WebFluxConfigurer {

		@Bean
		RequestIdHandlerFilterFunction requestIdHandlerFilterFunction() {
			return new RequestIdHandlerFilterFunction();
		}

		@Bean
		DisableBrowserCacheFilterFunction disableBrowserCacheFilterFunction() {
			return new DisableBrowserCacheFilterFunction();
		}
	}

	/**
	 * Gives each request its request id: the one in the {@code x-request-id} header, or a new one. The id is put in the
	 * Reactor context under {@link RequestId#CONTEXT_KEY} rather than in {@link RequestId}'s thread-bound state, since
	 * reactive requests share threads and move between them.
	 */
	static class RequestIdHandlerFilterFunction implements WebFilter {

		@Override
		public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
			final var requestId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(RequestId.HEADER_NAME))
				.map(String::trim)
				.filter(id -> !id.isEmpty())
				.orElseGet(() -> UUID.randomUUID().toString());

			exchange.getResponse().getHeaders().add(RequestId.HEADER_NAME, requestId);

			return chain.filter(exchange)
				.contextWrite(context -> context.put(RequestId.CONTEXT_KEY, requestId));
		}
	}

	static class DisableBrowserCacheFilterFunction implements WebFilter {

		@Override
		public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
			var headers = exchange.getResponse().getHeaders();

			headers.setCacheControl("no-store");
			headers.setExpires(0L);
			headers.setPragma("no-cache");

			return chain.filter(exchange);
		}
	}
}
