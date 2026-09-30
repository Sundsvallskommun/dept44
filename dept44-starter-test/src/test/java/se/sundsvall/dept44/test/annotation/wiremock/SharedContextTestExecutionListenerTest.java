package se.sundsvall.dept44.test.annotation.wiremock;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.wiremock.spring.internal.WireMockTestExecutionListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedContextTestExecutionListenerTest {

	@Mock
	private TestContext testContextMock;

	private final SharedContextTestExecutionListener listener = new SharedContextTestExecutionListener();
	private final SwitchableFileSource source = new SwitchableFileSource();

	@AfterEach
	void tearDown() {
		SwitchableFileSource.useNoFiles();
	}

	@Test
	void beforeTestClassSwitchesToFilesOfSharedClass() {
		when(testContextMock.getTestClass()).thenAnswer(_ -> SharedTestClass.class);

		listener.beforeTestClass(testContextMock);

		assertThat(Path.of(source.getPath())).endsWith(Path.of("test-classes", "__files"));
	}

	@Test
	void beforeTestMethodSwitchesToFilesOfSharedClass() {
		when(testContextMock.getTestClass()).thenAnswer(_ -> OtherSharedTestClass.class);

		listener.beforeTestMethod(testContextMock);

		assertThat(Path.of(source.getPath())).endsWith(Path.of("test-classes", "__files", "common"));
	}

	@Test
	void beforeTestMethodSwitchesToFilesOfEnclosingSharedClass() {
		when(testContextMock.getTestClass()).thenAnswer(_ -> SharedTestClass.NestedTestClass.class);

		listener.beforeTestMethod(testContextMock);

		assertThat(Path.of(source.getPath())).endsWith(Path.of("test-classes", "__files"));
	}

	@Test
	void classesNotSharingLeaveSourceAlone() {
		for (final var testClass : new Class<?>[] {
			NonSharedTestClass.class, SharedEmptyFilesTestClass.class, String.class
		}) {
			when(testContextMock.getTestClass()).thenAnswer(_ -> testClass);

			listener.beforeTestClass(testContextMock);
			listener.beforeTestMethod(testContextMock);

			assertThat(source.exists()).as(testClass.getName()).isFalse();
		}
	}

	@Test
	void runsBeforeWireMockResetsServer() {
		final var listeners = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
			.load(TestExecutionListener.class, (_, _, _) -> {
				// Listeners of other libraries missing their dependencies here are irrelevant
			});

		assertThat(listeners).hasAtLeastOneElementOfType(SharedContextTestExecutionListener.class);
		assertThat(listener.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE)
			.isLessThan(new WireMockTestExecutionListener().getOrder());
	}

	@WireMockAppTestSuite(files = "classpath:/__files/", classes = Object.class, sharedContext = true)
	private static class SharedTestClass {

		@Nested
		class NestedTestClass {
		}
	}

	@WireMockAppTestSuite(files = "classpath:/__files/common/", classes = Object.class, sharedContext = true)
	private static class OtherSharedTestClass {
	}

	@WireMockAppTestSuite(files = "classpath:/__files/", classes = Object.class)
	private static class NonSharedTestClass {
	}

	@WireMockAppTestSuite(files = "", classes = Object.class, sharedContext = true)
	private static class SharedEmptyFilesTestClass {
	}
}
