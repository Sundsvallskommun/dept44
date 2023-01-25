package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import se.sundsvall.dept44.requestid.RequestId;

class RequestIdInterceptorTest {

	@Mock
	private HttpRequest requestMock;

	@Mock
	private HttpContext contextMock;

	@BeforeEach
	public void initMocks() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testInheritance() {
		assertThat(new RequestIdInterceptor()).isInstanceOf(HttpRequestInterceptor.class);
	}

	@Test
	void testProcess() {
		// Call method
		new RequestIdInterceptor().process(requestMock, contextMock);

		// Verify mocks
		verify(requestMock).addHeader(RequestId.HEADER_NAME, RequestId.get());
		verifyNoInteractions(contextMock);
	}
}
