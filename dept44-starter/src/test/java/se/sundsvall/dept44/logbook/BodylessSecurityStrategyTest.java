package se.sundsvall.dept44.logbook;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.Sink;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BodylessSecurityStrategyTest {

	private final BodylessSecurityStrategy strategy = new BodylessSecurityStrategy();

	@Test
	void requestBodyIsNotCaptured() {
		final var request = mock(HttpRequest.class);

		strategy.process(request);

		verify(request).withoutBody();
	}

	@Test
	void responseBodyIsNotCaptured() throws IOException {
		final var response = mock(HttpResponse.class);

		strategy.process(mock(HttpRequest.class), response);

		verify(response).withoutBody();
		verify(response, never()).withBody();
	}

	@Test
	void requestIsNotWrittenBeforeResponse() {
		final var sink = mock(Sink.class);

		strategy.write(mock(Precorrelation.class), mock(HttpRequest.class), sink);

		verifyNoInteractions(sink);
	}

	@ParameterizedTest
	@ValueSource(ints = {
		401, 403
	})
	void rejectedRequestsAreWritten(final int status) throws IOException {
		final var sink = mock(Sink.class);
		final var correlation = mock(Correlation.class);
		final var request = mock(HttpRequest.class);
		final var response = mock(HttpResponse.class);
		when(response.getStatus()).thenReturn(status);

		strategy.write(correlation, request, response, sink);

		verify(sink).writeBoth(correlation, request, response);
	}

	@Test
	void otherRequestsAreNotWritten() throws IOException {
		final var sink = mock(Sink.class);
		final var response = mock(HttpResponse.class);
		when(response.getStatus()).thenReturn(200);

		strategy.write(mock(Correlation.class), mock(HttpRequest.class), response, sink);

		verifyNoInteractions(sink);
	}
}
