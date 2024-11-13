package se.sundsvall.dept44.common.validators.annotation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.common.validators.annotation.ValidPersonalNumber;

@ExtendWith(MockitoExtension.class)
class ValidPersonalNumberConstraintValidatorTest {

	@Mock
	private ValidPersonalNumber annotationMock;

	@InjectMocks
	private ValidPersonalNumberConstraintValidator validator;

	@Test
	void validPersonalNumber() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("199901011234")).isTrue();
		assertThat(validator.isValid("200010311234")).isTrue();
		assertThat(validator.isValid("199910311234", null)).isTrue();
		assertThat(validator.isValid("200010311234", null)).isTrue();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidPersonalNumber() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("210001011234")).isFalse();
		assertThat(validator.isValid("189901011234")).isFalse();
		assertThat(validator.isValid("not-valid")).isFalse();
		assertThat(validator.isValid("not-valid", null)).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void nullPersonalNumber() {

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
		assertThat(validator.getMessage()).isEqualTo("must match the regular expression ^(19|20)[0-9]{10}$");

		verifyNoInteractions(annotationMock);
	}
}
