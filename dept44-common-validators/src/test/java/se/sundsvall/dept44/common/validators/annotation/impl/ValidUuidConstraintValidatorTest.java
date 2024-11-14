package se.sundsvall.dept44.common.validators.annotation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.common.validators.annotation.ValidUuid;

@ExtendWith(MockitoExtension.class)
class ValidUuidConstraintValidatorTest {

	@Mock
	private ValidUuid annotationMock;

	@InjectMocks
	private ValidUuidConstraintValidator validator;

	@Test
	void validUuid() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid(UUID.randomUUID().toString())).isTrue();
		assertThat(validator.isValid(UUID.randomUUID().toString(), null)).isTrue();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidUuid() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("not-valid")).isFalse();
		assertThat(validator.isValid("not-valid", null)).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void nullUuid() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid(null)).isFalse();
		assertThat(validator.isValid(null, null)).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void nullablePropertySet() {

		// Mock
		when(annotationMock.nullable()).thenReturn(true);

		validator.initialize(annotationMock);

		assertThat(validator.isValid(null)).isTrue(); // null is treated as valid.
		assertThat(validator.isValid(null, null)).isTrue(); // null is treated as valid.
		assertThat(validator.isValid("not-valid")).isFalse(); // non-null and invalid values are still treated as invalid.
		assertThat(validator.isValid("not-valid", null)).isFalse(); // non-null and invalid values are still treated as invalid.

		verify(annotationMock).nullable();
	}

	@Test
	void testMessage() {
		assertThat(validator.getMessage()).isEqualTo("not a valid UUID");

		verifyNoInteractions(annotationMock);
	}
}
