package se.sundsvall.dept44.configuration.feign.logbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TempFileBodyTest {

	@TempDir
	private Path directory;

	@Test
	void keepsHeadAndRestUntilClosed() throws IOException {
		final var file = TempFileBody.createFile(directory);

		final var body = TempFileBody.fill(file, "ab".getBytes(UTF_8), new ByteArrayInputStream("cd".getBytes(UTF_8)));

		assertThat(body.length()).isEqualTo(4);
		assertThat(body.isRepeatable()).isTrue();
		assertThat(body.asInputStream().readAllBytes()).isEqualTo("abcd".getBytes(UTF_8));
		try (final Reader reader = body.asReader(UTF_8)) {
			assertThat(reader.read()).isEqualTo('a');
		}
		assertThat(Files.exists(file)).isTrue();

		body.close();

		assertThat(Files.exists(file)).isFalse();
	}

	@Test
	void fileIsDeletedWhenFillingFails() throws IOException {
		final var file = TempFileBody.createFile(directory);
		final var failing = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("connection reset");
			}
		};

		assertThatThrownBy(() -> TempFileBody.fill(file, new byte[0], failing)).isInstanceOf(IOException.class);
		assertThat(Files.exists(file)).isFalse();
	}

	@Test
	void defaultTemporaryDirectory() throws IOException {
		final var file = TempFileBody.createFile(null);
		try {
			assertThat(file.getFileName().toString()).startsWith("dept44-feign-error-");
			assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
		} finally {
			Files.deleteIfExists(file);
		}
	}

	@Test
	void fileIsOwnerOnly() throws IOException {
		final var file = TempFileBody.createFile(directory);

		assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
	}
}
