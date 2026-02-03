package se.sundsvall.dept44.test.annotation.wiremock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WireMockPathResolverTest {

	@Mock
	private WireMockServer wiremockMock;

	@Mock
	private Options optionsMock;

	@Mock
	private FileSource fileSourceMock;

	@Test
	void resolveMappingPathWithNonDefaultPath() {
		// Setup - WireMock has a custom path configured
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("classpath:custom/path");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, AnnotatedTestClass.class);

		// Verify - should use WireMock's path, normalized
		assertThat(result).isEqualTo("custom/path/");
	}

	@Test
	void resolveMappingPathWithDefaultPathAndAnnotation() {
		// Setup - WireMock has default path, but class has annotation
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("src/test/resources");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, AnnotatedTestClass.class);

		// Verify - should read from annotation and normalize
		assertThat(result).isEqualTo("TestClass/");
	}

	@Test
	void resolveMappingPathWithDefaultPathAndNoAnnotation() {
		// Setup - WireMock has default path, class has no annotation
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("src/test/resources");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, NonAnnotatedTestClass.class);

		// Verify - should return normalized default path
		assertThat(result).isEqualTo("src/test/resources/");
	}

	@Test
	void resolveMappingPathWithClasspathPrefixInAnnotation() {
		// Setup - WireMock has default path, annotation has classpath: prefix
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("src/test/resources");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, AnnotatedWithClasspathPrefix.class);

		// Verify - should strip classpath: prefix
		assertThat(result).isEqualTo("TestWithPrefix/");
	}

	@Test
	void resolveMappingPathWithLeadingSlashInAnnotation() {
		// Setup - WireMock has default path, annotation has leading slash
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("src/test/resources");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, AnnotatedWithLeadingSlash.class);

		// Verify - should remove leading slash
		assertThat(result).isEqualTo("LeadingSlashTest/");
	}

	@Test
	void resolveMappingPathWithTrailingSlashInAnnotation() {
		// Setup - WireMock has default path, annotation has trailing slash
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("src/test/resources");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, AnnotatedWithTrailingSlash.class);

		// Verify - should have exactly one trailing slash
		assertThat(result).isEqualTo("TrailingSlashTest/");
	}

	@Test
	void resolveMappingPathWithEmptyPath() {
		// Setup - WireMock returns empty path
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, NonAnnotatedTestClass.class);

		// Verify - should return empty string (no trailing slash for empty path)
		assertThat(result).isEmpty();
	}

	@Test
	void resolveMappingPathWithWindowsDefaultPath() {
		// Setup - WireMock has a Windows-style default path
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("src\\test\\resources");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, AnnotatedTestClass.class);

		// Verify - should detect default path and read from annotation
		assertThat(result).isEqualTo("TestClass/");
	}

	@Test
	void resolveMappingPathWithWindowsCustomPath() {
		// Setup - WireMock has Windows-style custom path
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("classpath:custom\\path\\to\\files");

		// Call
		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, AnnotatedTestClass.class);

		// Verify - should normalize backslashes to forward slashes
		assertThat(result).isEqualTo("custom/path/to/files/");
	}

	@ParameterizedTest
	@CsvSource({
		"path//to//file, path/to/file",
		"path///to////file, path/to/file",
		"//leading, /leading",
		"trailing//, trailing/",
		"no-doubles, no-doubles",
		"single/, single/",
		"'', ''"
	})
	void removeDoubleSlashes(final String input, final String expected) {
		assertThat(WireMockPathResolver.removeDoubleSlashes(input)).isEqualTo(expected);
	}

	// Test classes for annotation testing
	@WireMockAppTestSuite(files = "TestClass", classes = WireMockPathResolverTest.class)
	private static class AnnotatedTestClass {
	}

	private static class NonAnnotatedTestClass {
	}

	@WireMockAppTestSuite(files = "classpath:TestWithPrefix", classes = WireMockPathResolverTest.class)
	private static class AnnotatedWithClasspathPrefix {
	}

	@WireMockAppTestSuite(files = "/LeadingSlashTest", classes = WireMockPathResolverTest.class)
	private static class AnnotatedWithLeadingSlash {
	}

	@WireMockAppTestSuite(files = "TrailingSlashTest/", classes = WireMockPathResolverTest.class)
	private static class AnnotatedWithTrailingSlash {
	}
}
