package se.sundsvall.dept44.common.validators.annotation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.common.validators.annotation.ValidBase64;

@ExtendWith(MockitoExtension.class)
class ValidBase64ConstraintValidatorTest {

	@Mock
	private ValidBase64 mockAnnotation;

	@InjectMocks
	private ValidBase64ConstraintValidator validator;

	@Test
	void validBase64() {
		validator.initialize(mockAnnotation);

		assertThat(validator.isValid("QmFuYW4gTWVsb24gS2l3aSAmIENpdHJvbg==")).isTrue();
		assertThat(validator.isValid("QmFuYW4gTWVsb24gS2l3aSAmIENpdHJvbg==", null)).isTrue();

		verify(mockAnnotation).nullable();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"not-base64-encoded", ""
	})
	void invalidBase64(final String value) {
		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(value)).isFalse();
		assertThat(validator.isValid(value, null)).isFalse();

		verify(mockAnnotation).nullable();
	}

	@Test
	void nullBase64WhenNullableIsTrue() {
		when(mockAnnotation.nullable()).thenReturn(true);

		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(null)).isTrue(); // null is treated as valid.
		assertThat(validator.isValid(null, null)).isTrue(); // null is treated as valid.
		assertThat(validator.isValid("not-base64-encoded")).isFalse(); // non-null and invalid values are still treated
																		 // as invalid.
		assertThat(validator.isValid("not-base64-encoded", null)).isFalse(); // non-null and invalid values are still
																			 // treated as invalid.

		verify(mockAnnotation).nullable();
	}

	@Test
	void testMessage() {
		assertThat(validator.getMessage()).isEqualTo("not a valid BASE64-encoded string");

		verifyNoInteractions(mockAnnotation);
	}
}
