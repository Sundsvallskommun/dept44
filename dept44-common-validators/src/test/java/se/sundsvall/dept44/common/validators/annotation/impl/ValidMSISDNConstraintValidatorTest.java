package se.sundsvall.dept44.common.validators.annotation.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.common.validators.annotation.ValidMSISDN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ValidMSISDNConstraintValidatorTest {

	@Mock
	private ValidMSISDN annotationMock;

	@InjectMocks
	private ValidMSISDNConstraintValidator validator;

	@ParameterizedTest
	@ValueSource(strings = {"+46701234567", "+46721234567", "+46731234567", "+46761234567", "+46791234567", "+123456789012345", "+1234"})
	void validMSISDN(String number) {

		validator.initialize(annotationMock);

		assertThat(validator.isValid(number)).isTrue();
		assertThat(validator.isValid(number, null)).isTrue();

		verify(annotationMock).nullable();
	}

	@ParameterizedTest
	@ValueSource(strings = {"not-valid", "46701234567", "+06701234567", "+1234567890123456", "+123"})
	void invalidMSISDN(String number) {

		validator.initialize(annotationMock);

		assertThat(validator.isValid(number)).isFalse();
		assertThat(validator.isValid(number, null)).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void nullMSISDN() {

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
		assertThat(validator.getMessage()).isEqualTo("must be a valid MSISDN, regular expression ^\\+[1-9]{1}[0-9]{3,14}$");

		verifyNoInteractions(annotationMock);
	}
}
