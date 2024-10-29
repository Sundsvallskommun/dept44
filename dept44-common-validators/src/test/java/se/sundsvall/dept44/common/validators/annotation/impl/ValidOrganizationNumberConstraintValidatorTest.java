package se.sundsvall.dept44.common.validators.annotation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.sundsvall.dept44.common.validators.annotation.ValidOrganizationNumber;

@ExtendWith(MockitoExtension.class)
class ValidOrganizationNumberConstraintValidatorTest {

	@Mock
	private ValidOrganizationNumber annotationMock;

	@InjectMocks
	private ValidOrganizationNumberConstraintValidator validator;

	@ParameterizedTest
	@ValueSource(strings = {
		"1026112233", "2120122334", "3154812233", "5566112233", "7021112233", "8921112233", "9351112233"
	})
	void validOrganizationNumber(String orgNbr) {

		// Mock
		validator.initialize(annotationMock);

		assertThat(validator.isValid(orgNbr)).isTrue();
		assertThat(validator.isValid(orgNbr, null)).isTrue();

		verify(annotationMock).nullable();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"1006112233", "2110122334", "0921112233", "4566112233", "6021112233", "not-valid"
	})
	void invalidOrganizationNumber(String orgNbr) {

		validator.initialize(annotationMock);

		assertThat(validator.isValid(orgNbr)).isFalse();
		assertThat(validator.isValid(orgNbr, null)).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void nullOrganizationNumber() {

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
		assertThat(validator.getMessage()).isEqualTo("must match the regular expression ^([1235789][\\d][2-9]\\d{7})$");

		verifyNoInteractions(annotationMock);
	}
}
