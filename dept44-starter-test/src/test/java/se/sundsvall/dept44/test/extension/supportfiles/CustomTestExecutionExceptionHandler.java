package se.sundsvall.dept44.test.extension.supportfiles;

import java.util.List;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

public class CustomTestExecutionExceptionHandler implements TestExecutionExceptionHandler {

	private static final List<String> EXPECTED_TEST_ERRORS = List.of(

		"Failed to resolve parameter [se.sundsvall.dept44.test.extension.supportfiles.TestObject arg0] in method [void se.sundsvall.dept44.test.extension.ResourceLoaderExtensionTest$AnnotationExceptionTest.deserializeJsonWhenFileDoesntMatchObject(se.sundsvall.dept44.test.extension.supportfiles.TestObject)]: Unable to deserialize parameter from JSON",
		"Failed to resolve parameter [se.sundsvall.dept44.test.extension.supportfiles.TestObject arg0] in method [void se.sundsvall.dept44.test.extension.ResourceLoaderExtensionTest$AnnotationExceptionTest.deserializeXmlWhenFileDoesntMatchObject(se.sundsvall.dept44.test.extension.supportfiles.TestObject)]: Unable to deserialize parameter from XML",
		"Failed to resolve parameter [se.sundsvall.dept44.test.extension.supportfiles.TestObject arg0] in method [void se.sundsvall.dept44.test.extension.ResourceLoaderExtensionTest$AnnotationExceptionTest.deserializeWhenFileDoesntExist(se.sundsvall.dept44.test.extension.supportfiles.TestObject)]: Unable to load resource into parameter of type se.sundsvall.dept44.test.extension.supportfiles.TestObject");

	@Override
	public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {

		// If pre-defined errors occurs, ignore them. For all other errors, fail the test.
		if (EXPECTED_TEST_ERRORS.contains(throwable.getMessage())) {
			return;
		}

		throw throwable;
	}
}
