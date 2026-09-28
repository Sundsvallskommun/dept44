package se.sundsvall.dept44.test.annotation.wiremock;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Switches {@link SwitchableFileSource} to the files of a test class with {@link WireMockAppTestSuite#sharedContext()}
 * set, before the application context is loaded for the class and before the WireMock server is reset for each of its
 * tests, so that the server loads its default mappings from the files of that class.
 */
public class SharedContextTestExecutionListener extends AbstractTestExecutionListener {

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public void beforeTestClass(final TestContext testContext) {
		switchToFilesOf(testContext.getTestClass());
	}

	@Override
	public void beforeTestMethod(final TestContext testContext) {
		switchToFilesOf(testContext.getTestClass());
	}

	private static void switchToFilesOf(final Class<?> testClass) {
		final var suite = TestContextAnnotationUtils.findMergedAnnotation(testClass, WireMockAppTestSuite.class);
		if (suite != null && suite.sharedContext() && !suite.files().isEmpty()) {
			SwitchableFileSource.useClasspathDirectory(suite.files().replaceFirst("^classpath:", ""), testClass.getClassLoader());
		}
	}
}
