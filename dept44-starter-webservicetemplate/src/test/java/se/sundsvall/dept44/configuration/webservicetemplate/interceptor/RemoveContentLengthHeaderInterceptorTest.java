package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RemoveContentLengthHeaderInterceptorTest {

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
		assertThat(new RemoveContentLengthHeaderInterceptor()).isInstanceOf(HttpRequestInterceptor.class);
	}

	@Test
	void testProcess() {
		// Call method
		new RemoveContentLengthHeaderInterceptor().process(requestMock, contextMock);
		
		// Verify mocks
		verify(requestMock).removeHeaders(HTTP.CONTENT_LEN);
		verifyNoInteractions(contextMock);
	}
}
