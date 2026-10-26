package se.sundsvall.dept44.benchmarks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.helpers.NOPAppender;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.zalando.logbook.Logbook;
import se.sundsvall.dept44.configuration.LogbookConfiguration;
import se.sundsvall.dept44.configuration.ObjectMapperConfiguration;

import static java.util.Objects.requireNonNull;

/**
 * Starts the Logbook that {@link LogbookConfiguration} sets up, with dept44's defaults, as a service gets it.
 */
public final class LogbookContexts {

	static final String PAYLOAD_LOGGER = "se.sundsvall.dept44.payload";
	static final String CONSOLE_APPENDER = "CONSOLE";

	private static final String LOGSERVER_HOST = "LOGSERVER_HOST";
	private static final String LOGSERVER_PORT = "LOGSERVER_PORT";

	private LogbookContexts() {}

	/**
	 * Starts the Logbook with its payload log discarded at the logger.
	 *
	 * @param  properties properties on top of dept44's defaults, such as
	 *                    {@code logbook.body-filters.allowlist.enabled=false}
	 * @return            the started Logbook, to be closed when the benchmark is done
	 */
	public static StartedLogbook start(final List<String> properties) {
		return start(properties, LogOutput.DISCARD);
	}

	/**
	 * Starts the Logbook with its payload log written to the given output. Console output is written to a stream that
	 * discards it, after it has been encoded. GELF is sent to a log server on localhost that never reads what it is sent,
	 * so that its packets are dropped once its receive buffer is full, like those to a log server that cannot keep up.
	 *
	 * @param  properties properties on top of dept44's defaults
	 * @param  output     where the payload log goes
	 * @return            the started Logbook, to be closed when the benchmark is done
	 */
	public static StartedLogbook start(final List<String> properties, final LogOutput output) {
		final var logServer = output.gelf() ? openLogServer() : null;
		if (logServer != null) {
			// Read by logback-spring.xml, which only sets up GELF when LOGSERVER_HOST is defined
			System.setProperty(LOGSERVER_HOST, logServer.getLocalAddress().getHostAddress());
			System.setProperty(LOGSERVER_PORT, String.valueOf(logServer.getLocalPort()));
		}

		final var arguments = new ArrayList<String>();
		arguments.add("--logging.level.root=WARN");
		arguments.add("--dept44.logback.pii-masking.enabled=" + output.masked());
		properties.forEach(property -> arguments.add("--" + property));

		final ConfigurableApplicationContext context;
		try {
			context = new SpringApplicationBuilder(JacksonAutoConfiguration.class, ObjectMapperConfiguration.class, LogbookConfiguration.class)
				.web(WebApplicationType.NONE)
				.bannerMode(Banner.Mode.OFF)
				.logStartupInfo(false)
				.run(arguments.toArray(String[]::new));
		} catch (final RuntimeException e) {
			// There is no StartedLogbook to close, and a later start in the same JVM would otherwise send GELF to this port
			closeLogServer(logServer);
			throw e;
		}

		if (output == LogOutput.DISCARD) {
			discardPayloadLog();
		} else {
			payloadConsole().setOutputStream(OutputStream.nullOutputStream());
		}
		return new StartedLogbook(context, logServer);
	}

	/**
	 * @param  paths the allowlist paths
	 * @return       the property that sets them
	 */
	public static String allowlistPaths(final List<String> paths) {
		return "logbook.body-filters.allowlist.paths=" + String.join(",", paths);
	}

	/**
	 * @return the console appender of the payload log, as logback-spring.xml sets it up
	 */
	@SuppressWarnings("unchecked")
	static OutputStreamAppender<ILoggingEvent> payloadConsole() {
		final var logger = (Logger) LoggerFactory.getLogger(PAYLOAD_LOGGER);
		return requireNonNull((OutputStreamAppender<ILoggingEvent>) logger.getAppender(CONSOLE_APPENDER),
			"No console appender on the payload logger, has logback-spring.xml changed?");
	}

	private static void discardPayloadLog() {
		final var logger = (Logger) LoggerFactory.getLogger(PAYLOAD_LOGGER);
		logger.detachAndStopAllAppenders();
		logger.setAdditive(false);
		logger.setLevel(Level.TRACE);

		final var appender = new NOPAppender<ILoggingEvent>();
		appender.setContext(logger.getLoggerContext());
		appender.start();
		logger.addAppender(appender);
	}

	private static void closeLogServer(final DatagramSocket logServer) {
		if (logServer != null) {
			logServer.close();
			System.clearProperty(LOGSERVER_HOST);
			System.clearProperty(LOGSERVER_PORT);
		}
	}

	private static DatagramSocket openLogServer() {
		try {
			return new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
		} catch (final SocketException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * A started Logbook, and the log server its GELF output is sent to, if any.
	 */
	public static final class StartedLogbook implements AutoCloseable {

		private final ConfigurableApplicationContext context;
		private final DatagramSocket logServer;

		private StartedLogbook(final ConfigurableApplicationContext context, final DatagramSocket logServer) {
			this.context = context;
			this.logServer = logServer;
		}

		public Logbook logbook() {
			return context.getBean(Logbook.class);
		}

		/**
		 * @return the log server GELF is sent to, or null when the output has no GELF
		 */
		DatagramSocket logServer() {
			return logServer;
		}

		@Override
		public void close() {
			context.close();
			closeLogServer(logServer);
		}
	}
}
