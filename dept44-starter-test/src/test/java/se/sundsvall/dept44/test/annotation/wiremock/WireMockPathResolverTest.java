package se.sundsvall.dept44.test.annotation.wiremock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.Options;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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

	@ParameterizedTest
	@MethodSource("resolveMappingPathArguments")
	void resolveMappingPath(final String wiremockPath, final Class<?> testClass, final String expected) {
		when(wiremockMock.getOptions()).thenReturn(optionsMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn(wiremockPath);

		final var result = WireMockPathResolver.resolveMappingPath(wiremockMock, testClass);

		assertThat(result).isEqualTo(expected);
	}

	private static Stream<Arguments> resolveMappingPathArguments() {
		return Stream.of(
			Arguments.of("classpath:custom/path", AnnotatedTestClass.class, "custom/path/"),
			Arguments.of("src/test/resources", AnnotatedTestClass.class, "TestClass/"),
			Arguments.of("src/test/resources", NonAnnotatedTestClass.class, "src/test/resources/"),
			Arguments.of("src/test/resources", AnnotatedWithClasspathPrefix.class, "TestWithPrefix/"),
			Arguments.of("src/test/resources", AnnotatedWithLeadingSlash.class, "LeadingSlashTest/"),
			Arguments.of("src/test/resources", AnnotatedWithTrailingSlash.class, "TrailingSlashTest/"),
			Arguments.of("", NonAnnotatedTestClass.class, ""),
			Arguments.of("src\\test\\resources", AnnotatedTestClass.class, "TestClass/"),
			Arguments.of("classpath:custom\\path\\to\\files", AnnotatedTestClass.class, "custom/path/to/files/"));
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
