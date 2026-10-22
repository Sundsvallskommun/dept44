package se.sundsvall.dept44.benchmarks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zalando.logbook.Logbook;
import se.sundsvall.dept44.benchmarks.LogbookChainBenchmark.Calls;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * That each {@link LogOutput} writes the payload log where it says it does, masked when it says so, so that the
 * benchmarks measure what their names claim.
 */
class LogOutputTest {

	private static final String PARTY_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
	private static final String BODY = "{\"partyId\":\"" + PARTY_ID + "\"}";
	private static final List<String> FULL_BODIES = List.of("logbook.body-filters.allowlist.enabled=false");

	@ParameterizedTest
	@EnumSource(value = LogOutput.class, names = {
		"CONSOLE_MASKED", "CONSOLE_GELF_MASKED"
	})
	void consoleLineIsMaskedWhenMaskingIsOn(final LogOutput output) throws IOException {
		try (final var started = LogbookContexts.start(FULL_BODIES, output)) {
			final var console = captureConsole();

			logCall(started.logbook());

			assertThat(console.toString(UTF_8)).contains("partyId").doesNotContain(PARTY_ID);
		}
	}

	@ParameterizedTest
	@EnumSource(value = LogOutput.class, names = {
		"CONSOLE", "CONSOLE_GELF"
	})
	void consoleLineIsNotMaskedWhenMaskingIsOff(final LogOutput output) throws IOException {
		try (final var started = LogbookContexts.start(FULL_BODIES, output)) {
			final var console = captureConsole();

			logCall(started.logbook());

			assertThat(console.toString(UTF_8)).contains(PARTY_ID);
		}
	}

	@ParameterizedTest
	@EnumSource(value = LogOutput.class, names = {
		"CONSOLE_GELF", "CONSOLE_GELF_MASKED"
	})
	void gelfIsSentToTheLogServer(final LogOutput output) throws IOException {
		try (final var started = LogbookContexts.start(FULL_BODIES, output)) {
			logCall(started.logbook());

			final var message = receivePayloadLine(started.logServer());

			if (output.masked()) {
				assertThat(message).doesNotContain(PARTY_ID);
			} else {
				assertThat(message).contains(PARTY_ID);
			}
		}
	}

	private static ByteArrayOutputStream captureConsole() {
		final var console = new ByteArrayOutputStream();
		LogbookContexts.payloadConsole().setOutputStream(console);
		return console;
	}

	private static void logCall(final Logbook logbook) throws IOException {
		logbook.process(Calls.request())
			.write()
			.process(Calls.response(BODY))
			.write();
	}

	/**
	 * Other loggers may send to the log server too, so messages are read until one holds the payload log's response.
	 */
	private static String receivePayloadLine(final DatagramSocket logServer) throws IOException {
		logServer.setSoTimeout(5000);
		final var chunks = new HashMap<Long, byte[][]>();
		while (true) {
			final var packet = new DatagramPacket(new byte[65535], 65535);
			logServer.receive(packet);
			final var compressed = assemble(chunks, ByteBuffer.wrap(packet.getData(), 0, packet.getLength()));
			if (compressed != null) {
				try (final var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
					final var message = new String(gzip.readAllBytes(), UTF_8);
					if (message.contains("partyId")) {
						return message;
					}
				}
			}
		}
	}

	/**
	 * A GELF chunk starts with 0x1e 0x0f, an 8 byte message id, its sequence number and the number of chunks.
	 *
	 * @return the whole message once its last chunk has arrived, or null until then
	 */
	private static byte[] assemble(final Map<Long, byte[][]> chunks, final ByteBuffer packet) {
		if (packet.remaining() < 2 || packet.get(packet.position()) != 0x1e || packet.get(packet.position() + 1) != 0x0f) {
			final var message = new byte[packet.remaining()];
			packet.get(message);
			return message;
		}
		packet.position(packet.position() + 2);
		final var id = packet.getLong();
		final var sequence = packet.get();
		final var count = packet.get();
		final var payload = new byte[packet.remaining()];
		packet.get(payload);

		final var parts = chunks.computeIfAbsent(id, _ -> new byte[count][]);
		parts[sequence] = payload;
		if (Arrays.stream(parts).anyMatch(Objects::isNull)) {
			return null;
		}
		chunks.remove(id);
		final var message = new ByteArrayOutputStream();
		Arrays.stream(parts).forEach(message::writeBytes);
		return message.toByteArray();
	}
}
