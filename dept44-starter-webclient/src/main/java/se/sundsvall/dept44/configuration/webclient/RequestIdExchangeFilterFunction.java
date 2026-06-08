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

	@Override
	public Mono<ClientResponse> filter(final ClientRequest request, final ExchangeFunction next) {
		var builder = ClientRequest.from(request)
			.header(RequestId.HEADER_NAME, RequestId.get());

		Optional.ofNullable(Identifier.get())
			.map(Identifier::toHeaderValue)
			.ifPresent(value -> builder.header(Identifier.HEADER_NAME, value));

		return next.exchange(builder.build());
	}
}
