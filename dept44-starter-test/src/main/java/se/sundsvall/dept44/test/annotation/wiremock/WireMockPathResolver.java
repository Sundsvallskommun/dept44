package se.sundsvall.dept44.test.annotation.wiremock;

import static java.util.Objects.nonNull;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Resolves the WireMock files path for test classes using {@link WireMockAppTestSuite}.
 *
 * <p>
 * This is a workaround for wiremock-spring-boot not honoring Spring's {@code @AliasFor} annotation when
 * {@code @ConfigureWireMock} is used as a meta-annotation.
 *
 * <p>
 * wiremock-spring-boot only reads {@code @ConfigureWireMock} from inside {@code @EnableWireMock.value()}, not from
 * standalone meta-annotations. Since {@code @AliasFor} cannot alias into nested annotation values, we need to resolve
 * the path manually from
 * the {@link WireMockAppTestSuite#files()} attribute.
 *
 * @see <a href="https://github.com/wiremock/wiremock-spring-boot/issues/58">wiremock-spring-boot issue #58</a>
 */
public final class WireMockPathResolver {

	private static final String DEFAULT_WIREMOCK_PATH = "src/test/resources";
	private static final String CLASSPATH_PREFIX = "classpath:";

	private WireMockPathResolver() {
		// Utility class
	}

	/**
	 * Resolves the mapping path for WireMock stubs.
	 *
	 * <p>
	 * If wiremock-spring-boot is using its default path (indicating it didn't pick up the {@code @ConfigureWireMock}
	 * configuration), this method falls back to reading the path directly from {@link WireMockAppTestSuite#files()}.
	 *
	 * @param  wiremock  the WireMock server instance
	 * @param  testClass the test class to check for annotations
	 * @return           the normalized mapping path (without classpath: prefix, with trailing slash)
	 */
	public static String resolveMappingPath(final WireMockServer wiremock, final Class<?> testClass) {
		String path = wiremock.getOptions().filesRoot().getPath();

		// Workaround: If WireMock is using the default path, get a path from annotation directly
		// Normalize backslashes for Windows compatibility before comparing
		if (DEFAULT_WIREMOCK_PATH.equals(path.replace('\\', '/'))) {
			final var annotation = testClass.getAnnotation(WireMockAppTestSuite.class);
			if (nonNull(annotation)) {
				path = annotation.files();
			}
		}

		return normalizePath(path);
	}

	/**
	 * Removes duplicate slashes from a path.
	 *
	 * @param  path the path to normalize
	 * @return      the path with duplicate slashes removed
	 */
	public static String removeDoubleSlashes(final String path) {
		return path.replaceAll("//+", "/");
	}

	/**
	 * Normalizes a path by removing classpath: prefix and ensuring consistent forward slashes.
	 */
	private static String normalizePath(String path) {
		// Convert backslashes to forward slashes for Windows compatibility
		path = path.replace('\\', '/');

		// Strip "classpath:" prefix
		if (path.startsWith(CLASSPATH_PREFIX)) {
			path = path.substring(CLASSPATH_PREFIX.length());
		}

		// Remove leading slashes
		while (path.startsWith("/")) {
			path = path.substring(1);
		}

		// Remove trailing slashes
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}

		// Add exactly one trailing slash if path is not empty
		if (!path.isEmpty()) {
			path += "/";
		}

		return path;
	}
}
