package se.sundsvall.dept44.util;

import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceUtilsTest {

	private static final String EXCEPTION_MESSAGE = "testMessage";

	@Test
	void requireNonNullWhenNull() {
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNonNull(null, EXCEPTION_MESSAGE));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
	}

	@Test
	void requireNonNullWhenNotNull() {
		final var objectToCheck = "objectToCheck";
		final var returnObject = ResourceUtils.requireNonNull(objectToCheck, EXCEPTION_MESSAGE);

		assertThat(returnObject).isEqualTo(objectToCheck);
	}

	@Test
	void requireNotBlankWhenNull() {
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNotBlank(null, EXCEPTION_MESSAGE));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
	}

	@Test
	void requireNotBlankWhenBlank() {
		final var blankString = " ";
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNotBlank(blankString, EXCEPTION_MESSAGE));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
	}

	@Test
	void requireNotBlankWhenEmpty() {
		final var emptyString = "";
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNotBlank(emptyString, EXCEPTION_MESSAGE));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
	}

	@Test
	void requireNotBlankWhenNotBlank() {
		final var stringToCheck = "stringToCheck";
		final var returnString = ResourceUtils.requireNonNull(stringToCheck, EXCEPTION_MESSAGE);

		assertThat(returnString).isEqualTo(stringToCheck);
	}

	@Test
	void resourceAsString() {
		final var resource = new ClassPathResource("test-resource");
		final var expectedString = "This is a test-resource";
		final var returnString = ResourceUtils.asString(resource);

		assertThat(returnString).isEqualTo(expectedString);
	}

	@Test
	void resourceAsStringNonExistingResource() {
		final var resource = new ClassPathResource("non-existing");
		final var exception = assertThrows(UncheckedIOException.class, () -> ResourceUtils.asString(resource));

		assertThat(exception).isNotNull().isInstanceOf(UncheckedIOException.class);
		assertThat(exception.getMessage()).isEqualTo("java.io.FileNotFoundException: class path resource [non-existing] cannot be opened because it does not exist");
	}
}
