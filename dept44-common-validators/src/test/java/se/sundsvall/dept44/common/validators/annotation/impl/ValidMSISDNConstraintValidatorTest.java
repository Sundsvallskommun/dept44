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
	@ValueSource(strings = {"+46701234567", "+46721234567", "+46731234567", "+46761234567", "+46791234567"})
	void validMobileNumber(String number) {

		validator.initialize(annotationMock);

		assertThat(validator.isValid(number)).isTrue();
		assertThat(validator.isValid(number, null)).isTrue();

		verify(annotationMock).nullable();
	}

	@Test
	void validMSISDN() {
		validator.initialize(annotationMock);

		assertThat(validator.isValid("+123456789012345")).isTrue();
		assertThat(validator.isValid("+123456789012345", null)).isTrue();

		assertThat(validator.isValid("+1234")).isTrue();
		assertThat(validator.isValid("+1234", null)).isTrue();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidMobileNumber() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("not-valid")).isFalse();
		assertThat(validator.isValid("not-valid", null)).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void nullMobileNumber() {

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
		assertThat(validator.getMessage()).isEqualTo("must match the regular expression ^\\+[1-9]{1}[0-9]{3,14}$");

		verifyNoInteractions(annotationMock);
	}
}
