package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.Precorrelation;

@ExtendWith(MockitoExtension.class)
class NamedLoggerHttpLogWriterTest {

	@Mock
	private Logger loggerMock;

	@Mock
	private Correlation correlationMock;

	@Mock
	private Precorrelation precorrelationMock;

	@Test
	void writerWithCorrelation() {
		try (MockedStatic<LoggerFactory> loggerFactoryMock = Mockito.mockStatic(LoggerFactory.class)) {

			loggerFactoryMock.when(() -> LoggerFactory.getLogger(anyString())).thenReturn(loggerMock);

			final var namedLoggerHttpLogWriter = new LogbookConfiguration.NamedLoggerHttpLogWriter("testName");
			namedLoggerHttpLogWriter.write(correlationMock, "testResponse");

			verify(loggerMock).trace("testResponse");
		}
	}

	@Test
	void writerWithPreCorrelation() {
		try (MockedStatic<LoggerFactory> loggerFactoryMock = Mockito.mockStatic(LoggerFactory.class)) {

			loggerFactoryMock.when(() -> LoggerFactory.getLogger(anyString())).thenReturn(loggerMock);

			final var namedLoggerHttpLogWriter = new LogbookConfiguration.NamedLoggerHttpLogWriter("testName");

			namedLoggerHttpLogWriter.write(precorrelationMock, "testRequest");

			verify(loggerMock).trace("testRequest");
		}
	}

	@Test
	void isActive() {
		final var namedLoggerHttpLogWriter = new LogbookConfiguration.NamedLoggerHttpLogWriter("testName");
		assertThat(namedLoggerHttpLogWriter.isActive()).isFalse();
	}
}
