package se.sundsvall.dept44.test.annotation.wiremock;

import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.internal.WireMockContextCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

class WireMockAppTestSuiteContextCustomizerFactoryTest {

	private final WireMockAppTestSuiteContextCustomizerFactory factory = new WireMockAppTestSuiteContextCustomizerFactory();

	@Test
	void createContextCustomizerForAnnotatedClass() {
		final var result = factory.createContextCustomizer(AnnotatedTestClass.class, Collections.emptyList());

		assertThat(result).isInstanceOf(WireMockContextCustomizer.class);
	}

	@Test
	void createContextCustomizerForExistingClasspathPath() {
		final var result = factory.createContextCustomizer(ExistingPathTestClass.class, Collections.emptyList());

		assertThat(result).isInstanceOf(WireMockContextCustomizer.class);
	}

	@Test
	void createContextCustomizerForNonAnnotatedClass() {
		final var result = factory.createContextCustomizer(NonAnnotatedTestClass.class, Collections.emptyList());

		assertThat(result).isNull();
	}

	@Test
	void createContextCustomizerForEmptyFiles() {
		// Empty files still produce a customizer (annotation is present, just no filesUnderClasspath)
		final var result = factory.createContextCustomizer(EmptyFilesTestClass.class, Collections.emptyList());

		assertThat(result).isInstanceOf(WireMockContextCustomizer.class);
	}

	@Test
	void createContextCustomizerForPlainClass() {
		final var result = factory.createContextCustomizer(String.class, Collections.emptyList());

		assertThat(result).isNull();
	}

	@Test
	void resolveClasspathToFilesystemForExistingResource() {
		final var result = factory.resolveClasspathToFilesystem("classpath:/__files/", getClass().getClassLoader());

		assertThat(result).isNotNull().endsWith("__files");
		assertThat(new File(result)).isDirectory();
	}

	@Test
	void resolveClasspathToFilesystemForNonExistingResource() {
		final var result = factory.resolveClasspathToFilesystem("classpath:/NonExistentDir/", getClass().getClassLoader());

		assertThat(result).isNull();
	}

	@Test
	void resolveClasspathToFilesystemWithoutClasspathPrefix() {
		final var result = factory.resolveClasspathToFilesystem("__files/", getClass().getClassLoader());

		assertThat(result).isNotNull().endsWith("__files");
		assertThat(new File(result)).isDirectory();
	}

	@Test
	void resolveClasspathToFilesystemForEmptyPath() {
		final var result = factory.resolveClasspathToFilesystem("classpath:/", getClass().getClassLoader());

		// Empty resource name (root classpath) should return null — not a meaningful resolution
		assertThat(result).isNull();
	}

	@Test
	void synthesizeWithDirectoryOverridesFileAttributes() {
		final var original = AnnotatedElementUtils.findMergedAnnotation(ExistingPathTestClass.class, ConfigureWireMock.class);
		assertThat(original).isNotNull();

		final var result = factory.synthesizeWithDirectory(original, "/some/filesystem/path");

		assertThat(result.filesUnderClasspath()).isEmpty();
		assertThat(result.filesUnderDirectory()).containsExactly("/some/filesystem/path");
		// Other attributes are preserved
		assertThat(result.name()).isEqualTo(original.name());
		assertThat(result.port()).isEqualTo(original.port());
		assertThat(result.resetWireMockServer()).isEqualTo(original.resetWireMockServer());
	}

	@Test
	void resolveToFilesystemAnnotationFallsBackForNonExistingPath() {
		final var original = AnnotatedElementUtils.findMergedAnnotation(AnnotatedTestClass.class, ConfigureWireMock.class);
		assertThat(original).isNotNull();

		final var result = factory.resolveToFilesystemAnnotation(original, getClass().getClassLoader());

		// Should fall back to the original annotation since /SomeTestIT/ doesn't exist
		assertThat(result.filesUnderClasspath()).isEqualTo(original.filesUnderClasspath());
		assertThat(result.filesUnderDirectory()).isEmpty();
	}

	@Test
	void resolveToFilesystemAnnotationConvertsExistingPath() {
		final var original = AnnotatedElementUtils.findMergedAnnotation(ExistingPathTestClass.class, ConfigureWireMock.class);
		assertThat(original).isNotNull();

		final var result = factory.resolveToFilesystemAnnotation(original, getClass().getClassLoader());

		assertThat(result.filesUnderClasspath()).isEmpty();
		assertThat(result.filesUnderDirectory()).hasSize(1);
		assertThat(result.filesUnderDirectory()[0]).endsWith("__files");
		assertThat(new File(result.filesUnderDirectory()[0])).isDirectory();
	}

	@WireMockAppTestSuite(files = "classpath:/SomeTestIT/", classes = Object.class)
	private static class AnnotatedTestClass {
	}

	@ConfigureWireMock
	private static class NonAnnotatedTestClass {
	}

	@WireMockAppTestSuite(files = "", classes = Object.class)
	private static class EmptyFilesTestClass {
	}

	@WireMockAppTestSuite(files = "classpath:/__files/", classes = Object.class)
	private static class ExistingPathTestClass {
	}
}
