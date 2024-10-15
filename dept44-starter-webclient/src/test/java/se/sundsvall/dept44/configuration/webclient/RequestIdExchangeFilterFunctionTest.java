package se.sundsvall.dept44.configuration.webclient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;

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
}
