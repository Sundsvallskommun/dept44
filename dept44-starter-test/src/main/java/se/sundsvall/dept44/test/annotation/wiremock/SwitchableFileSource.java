package se.sundsvall.dept44.test.annotation.wiremock;

import com.github.tomakehurst.wiremock.common.BinaryFile;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.common.TextFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The file source of a WireMock server that several test classes share: it reads from the files of the test class that
 * is running, which {@link SharedContextTestExecutionListener} switches to before the server is reset for each test.
 * <p>
 * Every child reads through the directory in use when it is asked, so the files root and mappings WireMock takes at
 * start follow each switch. The directory is one per JVM, so test classes sharing a server must not run in parallel.
 * Jetty's serving of static files, which WireMock sets up from {@link #getPath()} at start, does not follow a switch.
 */
public final class SwitchableFileSource implements FileSource {

	private static final FileSource NO_FILES = new SingleRootFileSource(Path.of(System.getProperty("java.io.tmpdir"), "dept44-wiremock-no-files").toString());
	private static final AtomicReference<FileSource> CURRENT = new AtomicReference<>(NO_FILES);

	private final Supplier<FileSource> source;

	/**
	 * A file source reading from the directory in use, a directory that does not exist until the first switch.
	 */
	public SwitchableFileSource() {
		this(CURRENT::get);
	}

	private SwitchableFileSource(final Supplier<FileSource> source) {
		this.source = source;
	}

	/**
	 * Switches every switchable file source, and every child taken from one, to a directory.
	 *
	 * @param directory the directory to read from.
	 */
	public static void useDirectory(final String directory) {
		CURRENT.set(new SingleRootFileSource(directory));
	}

	/**
	 * Switches every switchable file source, and every child taken from one, to a directory found on the classpath. A
	 * directory on the file system is read as one, so that a {@code bodyFileName} may use {@code ..} within it. Any other,
	 * such as one in a jar or one that does not exist, is read through the classpath.
	 *
	 * @param classpathDirectory the directory, relative to the classpath root.
	 * @param classLoader        the class loader to find it with.
	 */
	public static void useClasspathDirectory(final String classpathDirectory, final ClassLoader classLoader) {
		final var name = classpathDirectory.replaceAll("^/+|/+$", "");
		CURRENT.set(fileSystemSource(name, classLoader).orElseGet(() -> new ClasspathFileSource(classLoader, name)));
	}

	/**
	 * Switches every switchable file source back to the directory that does not exist.
	 */
	public static void useNoFiles() {
		CURRENT.set(NO_FILES);
	}

	private static Optional<FileSource> fileSystemSource(final String name, final ClassLoader classLoader) {
		final var url = classLoader.getResource(name);
		if (url == null || !"file".equals(url.getProtocol())) {
			return Optional.empty();
		}
		try {
			return Optional.of(new SingleRootFileSource(Path.of(url.toURI()).toString()));
		} catch (final URISyntaxException | IllegalArgumentException _) {
			return Optional.empty();
		}
	}

	@Override
	public BinaryFile getBinaryFileNamed(final String name) {
		return source.get().getBinaryFileNamed(name);
	}

	@Override
	public TextFile getTextFileNamed(final String name) {
		return source.get().getTextFileNamed(name);
	}

	@Override
	public void createIfNecessary() {
		source.get().createIfNecessary();
	}

	@Override
	public FileSource child(final String subDirectoryName) {
		return new SwitchableFileSource(() -> source.get().child(subDirectoryName));
	}

	@Override
	public String getPath() {
		return source.get().getPath();
	}

	@Override
	public URI getUri() {
		return source.get().getUri();
	}

	@Override
	public List<TextFile> listFilesRecursively() {
		return source.get().listFilesRecursively();
	}

	@Override
	public void writeTextFile(final String name, final String contents) {
		source.get().writeTextFile(name, contents);
	}

	@Override
	public void writeBinaryFile(final String name, final byte[] contents) {
		source.get().writeBinaryFile(name, contents);
	}

	@Override
	public boolean exists() {
		return source.get().exists();
	}

	@Override
	public void deleteFile(final String name) {
		source.get().deleteFile(name);
	}
}
