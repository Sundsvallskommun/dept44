package se.sundsvall.dept44.configuration.feign.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.DefaultHttpLogFormatter;
import org.zalando.logbook.DefaultSink;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;

import feign.Feign;
import feign.FeignException;
import feign.Logger;
import se.sundsvall.dept44.configuration.feign.logging.util.FeignClient;
import se.sundsvall.dept44.configuration.feign.logging.util.FeignHttpServerRunner;
import se.sundsvall.dept44.configuration.feign.logging.util.TestStrategy;

@ExtendWith(MockitoExtension.class)
class FeignNullBodyFriendlyLogbookLoggerTest extends FeignHttpServerRunner {
	@Mock
	private HttpLogWriter writer;

	@Captor
	private ArgumentCaptor<String> requestCaptor;

	@Captor
	private ArgumentCaptor<String> responseCaptor;

	@Captor
	private ArgumentCaptor<Precorrelation> precorrelationCaptor;

	@Captor
	private ArgumentCaptor<Correlation> correlationCaptor;

	private FeignClient client;

	@BeforeEach
	void setup() {
		when(writer.isActive()).thenReturn(true);
		Logbook logbook = Logbook.builder().strategy(new TestStrategy())
				.sink(new DefaultSink(new DefaultHttpLogFormatter(), writer)).build();

		FeignNullBodyFriendlyLogbookLogger interceptor = new FeignNullBodyFriendlyLogbookLogger(logbook);

		client = Feign.builder().logger(interceptor).logLevel(Logger.Level.FULL).target(FeignClient.class, String.format("http://localhost:%s", port));
	}

	@Test
	void get200() throws IOException {
		client.getString();

		verify(writer).write(precorrelationCaptor.capture(), requestCaptor.capture());
		verify(writer).write(correlationCaptor.capture(), responseCaptor.capture());

		assertTrue(requestCaptor.getValue().contains("/get/string"));
		assertTrue(requestCaptor.getValue().contains("GET"));
		assertTrue(requestCaptor.getValue().contains("Remote: localhost"));
		assertTrue(requestCaptor.getValue().contains(precorrelationCaptor.getValue().getId()));

		assertEquals(precorrelationCaptor.getValue().getId(), correlationCaptor.getValue().getId());
		assertTrue(responseCaptor.getValue().contains(precorrelationCaptor.getValue().getId()));
		assertTrue(responseCaptor.getValue().contains("200 OK"));
		assertTrue(responseCaptor.getValue().contains("response"));
	}

	@Test
	void get200WithEmptyResponseBody() {
		client.getVoid();
	}

	@Test
	void get200WithNonEmptyResponseBody() {
		String response = "response";
		String actualResponseBody = client.getString();

		assertEquals(response, actualResponseBody);
	}

	@Test
	void post400() throws IOException {
		assertThrows(FeignException.BadRequest.class, () -> client.postBadRequest("request"));

		verify(writer).write(precorrelationCaptor.capture(), requestCaptor.capture());
		verify(writer).write(correlationCaptor.capture(), responseCaptor.capture());

		assertTrue(requestCaptor.getValue().contains("/post/bad-request"));
		assertTrue(requestCaptor.getValue().contains("POST"));
		assertTrue(requestCaptor.getValue().contains("Remote: localhost"));
		assertTrue(requestCaptor.getValue().contains(precorrelationCaptor.getValue().getId()));
		assertTrue(requestCaptor.getValue().contains("request"));

		assertEquals(precorrelationCaptor.getValue().getId(), correlationCaptor.getValue().getId());
		assertTrue(responseCaptor.getValue().contains(precorrelationCaptor.getValue().getId()));
		assertTrue(responseCaptor.getValue().contains("400 Bad Request"));
		assertTrue(responseCaptor.getValue().contains("response"));
	}
}
