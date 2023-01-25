package se.sundsvall.dept44.configuration.feign.logging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.zalando.logbook.Logbook.builder;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.RequestWritingStage;
import org.zalando.logbook.Logbook.ResponseProcessingStage;

import feign.Feign;
import feign.Logger;
import feign.RetryableException;
import se.sundsvall.dept44.configuration.feign.logging.util.FeignClient;
import se.sundsvall.dept44.configuration.feign.logging.util.FeignHttpServerRunner;
import se.sundsvall.dept44.configuration.feign.logging.util.TestStrategy;

@ExtendWith(MockitoExtension.class)
class FeignNullBodyFriendlyLogbookLoggerExceptionTest extends FeignHttpServerRunner {
	@Mock
	private Logbook logbook;

	@Mock
	private RequestWritingStage requestStage;
	@Mock
	private ResponseProcessingStage responseStage;

	private FeignClient client;
	
	private FeignNullBodyFriendlyLogbookLogger interceptor;

	@BeforeEach
	void setup() {
		logbook = spy(builder().strategy(new TestStrategy()).build());
		interceptor = new FeignNullBodyFriendlyLogbookLogger(logbook);
		client = Feign.builder().logger(interceptor).logLevel(Logger.Level.FULL).target(FeignClient.class, String.format("http://localhost:%s", port));
	}

	@Test
	void requestThrowsIOException() throws IOException {
		doThrow(IOException.class).when(logbook).process(any());
		
		assertThrows(UncheckedIOException.class, client::getString);
	}

	@Test
	void responseThrowsIOException() throws IOException {
		when(logbook.process(any())).thenReturn(requestStage);
		when(requestStage.write()).thenReturn(responseStage);
		doThrow(IOException.class).when(responseStage).process(any());

		assertThrows(UncheckedIOException.class, client::getString);
	}

	@Test
	void ioExceptionIsLogged() {
		FeignClient invalidClient = Feign.builder().logger(interceptor).logLevel(Logger.Level.FULL).target(FeignClient.class, "http://localhost:29999");

		assertThrows(RetryableException.class, invalidClient::getString);
	}
}
