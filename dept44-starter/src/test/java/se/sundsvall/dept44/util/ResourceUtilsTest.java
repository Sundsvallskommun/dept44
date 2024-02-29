package se.sundsvall.dept44.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ResourceUtilsTest {

	@Test
	void requireNonNullWhenNull() {
		final var exceptionMessage = "testMessage";
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNonNull(null, exceptionMessage));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(exceptionMessage);
	}

	@Test
	void requireNonNullWhenNotNull() {
		final var exceptionMessage = "testMessage";
		final var objectToCheck = "objectToCheck";
		final var returnObject = ResourceUtils.requireNonNull(objectToCheck, exceptionMessage);

		assertThat(returnObject).isEqualTo(objectToCheck);
	}

	@Test
	void requireNotBlankWhenNull() {
		final var exceptionMessage = "testMessage";
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNotBlank(null, exceptionMessage));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(exceptionMessage);
	}

	@Test
	void requireNotBlankWhenBlank() {
		final var exceptionMessage = "testMessage";
		final var blankString = " ";
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNotBlank(blankString, exceptionMessage));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(exceptionMessage);
	}

	@Test
	void requireNotBlankWhenEmpty() {
		final var exceptionMessage = "testMessage";
		final var emptyString = "";
		final var illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> ResourceUtils.requireNotBlank(emptyString, exceptionMessage));

		assertThat(illegalArgumentException.getMessage()).isEqualTo(exceptionMessage);
	}

	@Test
	void requireNotBlankWhenNotBlank() {
		final var exceptionMessage = "testMessage";
		final var stringToCheck = "stringToCheck";
		final var returnString = ResourceUtils.requireNonNull(stringToCheck, exceptionMessage);

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
