package se.sundsvall.dept44.test.annotation.wiremock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.ConfigureWireMocks;
import org.wiremock.spring.internal.WireMockContextCustomizer;

/**
 * A Spring {@link ContextCustomizerFactory} that properly resolves the {@code filesUnderClasspath} attribute from
 * {@link WireMockAppTestSuite} via Spring's {@code @AliasFor} mechanism, and converts it to a filesystem-backed
 * {@code filesUnderDirectory} to
 * support {@code ..} path traversal in WireMock {@code bodyFileName} references.
 *
 * <p>
 * The wiremock-spring-boot library's built-in {@code WireMockContextCustomizerFactory} uses JUnit 5's
 * {@code AnnotationSupport.findRepeatableAnnotations()} to discover {@link ConfigureWireMock}, which does
 * <b>not</b> resolve Spring's {@code @AliasFor}. This means the {@code files} attribute on
 * {@link WireMockAppTestSuite} never propagates to {@code filesUnderClasspath}, and WireMock defaults to
 * {@code src/test/resources}.
 *
 * <p>
 * Additionally, {@code filesUnderClasspath} creates a {@code ClasspathFileSource} which does not support {@code ..}
 * path traversal in {@code bodyFileName} — the {@code ..} is treated as a literal path segment. This factory resolves
 * the classpath resource
 * to a filesystem directory and synthesizes a {@link ConfigureWireMock} with {@code filesUnderDirectory} instead, which
 * creates a {@code SingleRootFileSource} that natively supports {@code ..}.
 *
 * <p>
 * This factory runs before the built-in one (via {@link Order}) and uses Spring's
 * {@link AnnotatedElementUtils#findMergedRepeatableAnnotations} to produce synthesized {@link ConfigureWireMock}
 * instances. The resulting {@link WireMockContextCustomizer}
 * creates the WireMock server(s) with the correct file source. When the built-in factory's customizer runs second, it
 * finds the server already exists in the store and skips creation.
 *
 * <p>
 * For a class with {@link WireMockAppTestSuite#sharedContext()} set, the server is configured without the files of the
 * class and with a {@link SwitchableFileSource} instead, so that classes otherwise configured alike get the same
 * context key and share one application context.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WireMockAppTestSuiteContextCustomizerFactory implements ContextCustomizerFactory {

	private static final Logger LOG = LoggerFactory.getLogger(WireMockAppTestSuiteContextCustomizerFactory.class);

	@Override
	public @Nullable ContextCustomizer createContextCustomizer(final Class<?> testClass, final List<ContextConfigurationAttributes> configAttributes) {
		if (!AnnotatedElementUtils.hasAnnotation(testClass, WireMockAppTestSuite.class)) {
			return null;
		}

		final var merged = AnnotatedElementUtils.findMergedRepeatableAnnotations(testClass, ConfigureWireMock.class, ConfigureWireMocks.class);
		if (merged.isEmpty()) {
			return null;
		}

		final var shared = AnnotatedElementUtils.findMergedAnnotation(testClass, WireMockAppTestSuite.class).sharedContext();

		final var resolved = merged.stream()
			.map(annotation -> {
				if (annotation.filesUnderClasspath().isEmpty())
					return annotation;
				if (shared)
					return synthesizeShared(annotation);
				return resolveToFilesystemAnnotation(annotation, testClass.getClassLoader());
			})
			.toList();

		return new WireMockContextCustomizer(resolved);
	}

	/**
	 * The configuration of a server shared by several test classes: no files of any one class, which would give every
	 * class a context of its own, and a file source each test switches to the files of its class.
	 */
	ConfigureWireMock synthesizeShared(final ConfigureWireMock original) {
		final var attributes = new LinkedHashMap<>(AnnotationUtils.getAnnotationAttributes(original));
		attributes.put("filesUnderClasspath", "");
		attributes.put("filesUnderDirectory", new String[0]);
		final var customizers = new ArrayList<>(List.of(original.configurationCustomizers()));
		customizers.add(SwitchableFileSourceCustomizer.class);
		attributes.put("configurationCustomizers", customizers.toArray(Class<?>[]::new));
		return AnnotationUtils.synthesizeAnnotation(attributes, ConfigureWireMock.class, null);
	}

	ConfigureWireMock resolveToFilesystemAnnotation(final ConfigureWireMock original, final ClassLoader classLoader) {
		final var classpathValue = original.filesUnderClasspath();
		final var filesystemPath = resolveClasspathToFilesystem(classpathValue, classLoader);

		if (filesystemPath == null) {
			LOG.warn("Could not resolve classpath resource '{}' to filesystem path. "
				+ "WireMock bodyFileName paths using '..' will not work.", classpathValue);
			return original;
		}

		return synthesizeWithDirectory(original, filesystemPath);
	}

	String resolveClasspathToFilesystem(final String classpathValue, final ClassLoader classLoader) {
		var path = classpathValue;
		if (path.startsWith("classpath:")) {
			path = path.substring("classpath:".length());
		}
		// ClassLoader.getResource() does not use leading slashes
		var resourceName = path.replaceFirst("^/+", "");
		// Strip trailing slashes for a consistent directory lookup
		while (resourceName.endsWith("/")) {
			resourceName = resourceName.substring(0, resourceName.length() - 1);
		}

		if (resourceName.isEmpty()) {
			return null;
		}

		final var url = classLoader.getResource(resourceName);

		if (url == null) {
			return null;
		}

		try {
			return java.nio.file.Paths.get(url.toURI()).toAbsolutePath().toString();
		} catch (final Exception e) {
			LOG.debug("Failed to convert URL '{}' to filesystem path", url, e);
			return null;
		}
	}

	ConfigureWireMock synthesizeWithDirectory(final ConfigureWireMock original, final String directory) {
		final var attributes = new LinkedHashMap<>(AnnotationUtils.getAnnotationAttributes(original));
		attributes.put("filesUnderClasspath", "");
		attributes.put("filesUnderDirectory", new String[] {
			directory
		});
		return AnnotationUtils.synthesizeAnnotation(attributes, ConfigureWireMock.class, null);
	}
}
