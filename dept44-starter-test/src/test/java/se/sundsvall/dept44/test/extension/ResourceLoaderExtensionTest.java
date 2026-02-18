package se.sundsvall.dept44.test.extension;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import se.sundsvall.dept44.test.annotation.resource.Load;
import se.sundsvall.dept44.test.extension.supportfiles.CustomTestExecutionExceptionHandler;
import se.sundsvall.dept44.test.extension.supportfiles.TestObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ResourceLoaderExtension.class)
class ResourceLoaderExtensionTest {

	private static final String TEST_JSON_FILE = "test.json";
	private static final String TEST_XML_FILE = "test.xml";

	@Test
	void deserializeJson(@Load(value = TEST_JSON_FILE, as = Load.ResourceType.JSON) final TestObject testObject) {
		assertThat(testObject).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(testObject.getKey()).isEqualTo("this-is-a-key");
		assertThat(testObject.getValue()).isEqualTo("this-is-a-value");
		assertThat(testObject.getOffsetDateTime()).isAtSameInstantAs(OffsetDateTime.parse("2022-01-01T00:00:00+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
	}

	@Test
	void deserializeXml(@Load(value = TEST_XML_FILE, as = Load.ResourceType.XML) final TestObject testObject) {
		assertThat(testObject).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(testObject.getKey()).isEqualTo("this-is-a-key");
		assertThat(testObject.getValue()).isEqualTo("this-is-a-value");
		assertThat(testObject.getOffsetDateTime()).isAtSameInstantAs(OffsetDateTime.parse("2022-01-01T00:00:00+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
	}

	@Test
	void deserializeString(@Load(TEST_JSON_FILE) final String testObject) {
		assertThat(testObject).isEqualToIgnoringWhitespace("""
			{
				"offsetDateTime": "2022-01-01T00:00:00+02:00",
				"value": "this-is-a-value",
				"key": "this-is-a-key"
			}
			""");
	}

	/**
	 * @see CustomTestExecutionExceptionHandler for the tests below. This class is necessary in these tests due to the
	 *      testing of functionality that happens before the actual test implementation. We must "swallow" the exceptions
	 *      generated during the
	 *      annotation-processing on the method parameters.
	 */
	@Nested
	@ExtendWith(CustomTestExecutionExceptionHandler.class)
	class AnnotationExceptionTest {

		@Test
		void deserializeJsonWhenFileDoesntMatchObject(@Load(value = TEST_XML_FILE, as = Load.ResourceType.JSON) final TestObject testObject) {
			fail("Should have failed when the method parameters was resolved!");
		}

		@Test
		void deserializeXmlWhenFileDoesntMatchObject(@Load(value = TEST_JSON_FILE, as = Load.ResourceType.XML) final TestObject testObject) {
			fail("Should have failed when the method parameters was resolved!");
		}

		@Test
		void deserializeWhenFileDoesntExist(@Load("not-existing.xml") final TestObject testObject) {
			fail("Should have failed when the method parameters was resolved!");
		}
	}
}
