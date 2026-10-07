package se.sundsvall.dept44.configuration.feign.logbook;

import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Set;

/**
 * Response body kept in a temporary file instead of in memory, for an error body that is larger than the capture limit
 * but must stay readable more than once by error decoders.
 * <p>
 * The file is created readable and writable by the owner of the process only, and is deleted when the body is closed,
 * which Feign does once the error has been decoded.
 */
final class TempFileBody implements Response.Body {

	private static final String PREFIX = "dept44-feign-error-";
	private static final String SUFFIX = ".body";
	private static final String POSIX = "posix";
	private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

	private final Path file;
	private final long size;

	private TempFileBody(final Path file, final long size) {
		this.file = file;
		this.size = size;
	}

	/**
	 * Creates the file readable and writable by the owner only. Error bodies may hold personal data, and the default
	 * temporary directory is shared, so a file system without POSIX permissions gets no file at all: the caller then
	 * streams the body instead.
	 */
	static Path createFile(final Path directory) throws IOException {
		final var fileSystem = Optional.ofNullable(directory).map(Path::getFileSystem).orElseGet(FileSystems::getDefault);
		if (!fileSystem.supportedFileAttributeViews().contains(POSIX)) {
			throw new IOException("Owner-only temporary files need POSIX file permissions");
		}
		if (directory == null) {
			return Files.createTempFile(PREFIX, SUFFIX, OWNER_ONLY);
		}
		return Files.createTempFile(directory, PREFIX, SUFFIX, OWNER_ONLY);
	}

	/**
	 * Writes the bytes already read, followed by the rest of the body, to the file. The file is deleted if that fails.
	 */
	static TempFileBody fill(final Path file, final byte[] head, final InputStream rest) throws IOException {
		try (final var output = Files.newOutputStream(file)) {
			output.write(head);
			rest.transferTo(output);
		} catch (final IOException | RuntimeException e) {
			Files.deleteIfExists(file);
			throw e;
		}
		return new TempFileBody(file, Files.size(file));
	}

	@Override
	public Integer length() {
		if (size > Integer.MAX_VALUE) {
			return null;
		}
		return (int) size;
	}

	@Override
	public boolean isRepeatable() {
		return true;
	}

	@Override
	public InputStream asInputStream() throws IOException {
		return Files.newInputStream(file);
	}

	@Override
	public Reader asReader(final Charset charset) throws IOException {
		return new InputStreamReader(asInputStream(), charset);
	}

	@Override
	public void close() throws IOException {
		Files.deleteIfExists(file);
	}
}
