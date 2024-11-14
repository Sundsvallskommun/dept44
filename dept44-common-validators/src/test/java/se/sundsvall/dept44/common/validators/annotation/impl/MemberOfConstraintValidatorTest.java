package se.sundsvall.dept44.common.validators.annotation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.common.validators.annotation.MemberOf;

@ExtendWith(MockitoExtension.class)
class MemberOfConstraintValidatorTest {

	enum AddressType {
		HOME,
		WORK
	}

	@Mock
	private MemberOf mockAnnotation;

	@Mock
	private HibernateConstraintValidatorContext mockContext;

	@InjectMocks
	private MemberOfConstraintValidator validator;

	@BeforeEach
	void setUp() {
		when(mockAnnotation.value()).thenAnswer(invocation -> AddressType.class);
		when(mockAnnotation.caseSensitive()).thenReturn(true);
		when(mockAnnotation.nullable()).thenReturn(false);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"HOME", "WORK"
	})
	void allowedValues(final String value) {
		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(value)).isTrue();
		assertThat(validator.isValid(value, mockContext)).isTrue();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockAnnotation).caseSensitive();
		verifyNoInteractions(mockContext);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"home", "work"
	})
	void allowedValuesWhenCaseSensitiveIsFalse(final String value) {
		when(mockAnnotation.caseSensitive()).thenReturn(false);

		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(value)).isTrue();
		assertThat(validator.isValid(value, mockContext)).isTrue();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockAnnotation).caseSensitive();
		verifyNoInteractions(mockContext);
	}

	@Test
	void disallowedValues() {
		when(mockContext.unwrap(HibernateConstraintValidatorContext.class)).thenReturn(mockContext);

		validator.initialize(mockAnnotation);

		assertThat(validator.isValid("NOT_AN_ADDRESS_TYPE")).isFalse();
		assertThat(validator.isValid("NOT_AN_ADDRESS_TYPE", mockContext)).isFalse();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockAnnotation).caseSensitive();
		verify(mockContext).unwrap(HibernateConstraintValidatorContext.class);
		verify(mockContext, times(2)).addMessageParameter(any(String.class), any());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"home", "work"
	})
	void disallowedValuesWhenCaseSensitiveIsTrue(final String value) {
		when(mockContext.unwrap(HibernateConstraintValidatorContext.class)).thenReturn(mockContext);

		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(value)).isFalse();
		assertThat(validator.isValid(value, mockContext)).isFalse();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockAnnotation).caseSensitive();
		verify(mockContext).unwrap(HibernateConstraintValidatorContext.class);
		verify(mockContext, times(2)).addMessageParameter(any(String.class), any());
	}

	@Test
	void nullValueWhenNullableIsFalse() {
		when(mockContext.unwrap(HibernateConstraintValidatorContext.class)).thenReturn(mockContext);

		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(null)).isFalse();
		assertThat(validator.isValid(null, mockContext)).isFalse();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockAnnotation).caseSensitive();
		verify(mockContext).unwrap(HibernateConstraintValidatorContext.class);
		verify(mockContext, times(2)).addMessageParameter(any(String.class), any());
	}

	@Test
	void nullValueWhenNullableIsTrue() {
		when(mockAnnotation.nullable()).thenReturn(true);

		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(null)).isTrue();
		assertThat(validator.isValid(null, mockContext)).isTrue();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockAnnotation).caseSensitive();
		verifyNoInteractions(mockContext);
	}

	@Test
	void testMessage() {
		when(mockAnnotation.caseSensitive()).thenReturn(false);

		validator.initialize(mockAnnotation);

		assertThat(validator.getMessage()).isEqualTo("must be one of: " + Arrays.toString(AddressType.values()));

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockAnnotation).caseSensitive();
	}
}
