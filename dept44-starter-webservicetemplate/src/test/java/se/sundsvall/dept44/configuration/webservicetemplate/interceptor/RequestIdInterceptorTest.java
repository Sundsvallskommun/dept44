package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.sundsvall.dept44.requestid.RequestId;

@ExtendWith(MockitoExtension.class)
class RequestIdInterceptorTest {

	@Mock
	private HttpRequest mockHttpRequest;

	@Mock
	private EntityDetails mockEntityDetails;

	@Mock
	private HttpContext mockHttpContext;

	@Test
	void testInheritance() {
		assertThat(new RequestIdInterceptor()).isInstanceOf(HttpRequestInterceptor.class);
	}

	@Test
	void testProcess() {
		// Call method
		new RequestIdInterceptor().process(mockHttpRequest, mockEntityDetails, mockHttpContext);

		// Verify mocks
		verify(mockHttpRequest).addHeader(RequestId.HEADER_NAME, RequestId.get());
		verifyNoInteractions(mockHttpContext);
	}
}
