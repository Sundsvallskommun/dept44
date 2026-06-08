package se.sundsvall.dept44.configuration.webclient;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.dept44.support.Identifier.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestIdExchangeFilterFunctionTest {

	@Mock
	private ClientRequest requestMock;

	@Mock
	private HttpHeaders headersMock;

	@Mock
	private ExchangeFunction functionMock;

	@BeforeEach
	void initMocks() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testFilter() {
		when(requestMock.headers()).thenReturn(headersMock);
		when(requestMock.cookies()).thenReturn(new LinkedMultiValueMap<>());

		new RequestIdExchangeFilterFunction().filter(requestMock, functionMock);

		verify(functionMock).exchange(any(ClientRequest.class));
	}

	@Test
	void testFilterPropagatesIdentifier() {
		final var request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost")).build();
		final var requestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
		when(functionMock.exchange(requestCaptor.capture())).thenReturn(Mono.empty());

		try {
			Identifier.set(Identifier.create().withType(Type.AD_ACCOUNT).withValue("joe01doe"));

			new RequestIdExchangeFilterFunction().filter(request, functionMock).block();
		} finally {
			Identifier.remove();
		}

		assertThat(requestCaptor.getValue().headers().getFirst(Identifier.HEADER_NAME))
			.isEqualTo("joe01doe; type=adAccount");
	}

	@Test
	void testFilterWithoutIdentifierOmitsHeader() {
		final var request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost")).build();
		final var requestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
		when(functionMock.exchange(requestCaptor.capture())).thenReturn(Mono.empty());

		new RequestIdExchangeFilterFunction().filter(request, functionMock).block();

		assertThat(requestCaptor.getValue().headers().containsHeader(Identifier.HEADER_NAME)).isFalse();
	}
}
