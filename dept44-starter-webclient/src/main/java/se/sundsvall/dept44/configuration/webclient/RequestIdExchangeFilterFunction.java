package se.sundsvall.dept44.configuration.webclient;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import se.sundsvall.dept44.requestid.RequestId;

class RequestIdExchangeFilterFunction implements ExchangeFilterFunction {

	@Override
	public Mono<ClientResponse> filter(final ClientRequest request, final ExchangeFunction next) {
		var newRequest = ClientRequest.from(request)
			.header(RequestId.HEADER_NAME, RequestId.get())
			.build();

		return next.exchange(newRequest);
	}
}
