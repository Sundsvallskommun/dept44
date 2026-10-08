package se.sundsvall.dept44.configuration.webclient;

import java.util.Optional;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

class RequestIdExchangeFilterFunction implements ExchangeFilterFunction {

	/**
	 * The request id comes from the Reactor context when the call is made while handling a reactive request, otherwise
	 * from {@link RequestId}.
	 */
	@Override
	public Mono<ClientResponse> filter(final ClientRequest request, final ExchangeFunction next) {
		return Mono.deferContextual(context -> {
			final var builder = ClientRequest.from(request);

			context.<String>getOrEmpty(RequestId.CONTEXT_KEY)
				.or(() -> Optional.ofNullable(RequestId.get()))
				.ifPresent(requestId -> builder.header(RequestId.HEADER_NAME, requestId));

			Optional.ofNullable(Identifier.get())
				.map(Identifier::toHeaderValue)
				.ifPresent(value -> builder.header(Identifier.HEADER_NAME, value));

			return next.exchange(builder.build());
		});
	}
}
