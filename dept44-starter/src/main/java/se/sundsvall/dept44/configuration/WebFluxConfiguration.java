package se.sundsvall.dept44.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
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
	@EnableWebFlux
	static class WebFluxConfig implements WebFluxConfigurer {

		@Bean
		RequestIdHandlerFilterFunction requestIdHandlerFilterFunction() {
			return new RequestIdHandlerFilterFunction();
		}
	}

	static class RequestIdHandlerFilterFunction implements WebFilter {

		@Override
		public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
			var requestId = exchange.getRequest().getHeaders().getFirst(RequestId.HEADER_NAME);

			RequestId.init(requestId);

			exchange.getResponse().getHeaders().add(RequestId.HEADER_NAME, RequestId.get());

			return chain.filter(exchange)
				.then()
				.doFinally(ignoredSignalType -> RequestId.reset());
		}
	}
}
