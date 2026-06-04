package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.dept44.support.Identifier.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

	@Test
	void testProcessPropagatesIdentifier() {
		try {
			Identifier.set(Identifier.create().withType(Type.AD_ACCOUNT).withValue("joe01doe"));

			new RequestIdInterceptor().process(mockHttpRequest, mockEntityDetails, mockHttpContext);

			verify(mockHttpRequest).addHeader(RequestId.HEADER_NAME, RequestId.get());
			verify(mockHttpRequest).addHeader(Identifier.HEADER_NAME, "joe01doe; type=adAccount");
			verifyNoInteractions(mockHttpContext);
		} finally {
			Identifier.remove();
		}
	}
}
