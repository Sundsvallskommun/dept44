package se.sundsvall.dept44.common.validators.annotation.impl;

import java.util.List;
import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.common.validators.annotation.OneOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OneOfConstraintValidatorTest {

	@Mock
	private OneOf mockAnnotation;

	@Mock
	private ConstraintValidatorContextImpl mockContext;

	@InjectMocks
	private OneOfConstraintValidator validator;

	@BeforeEach
	void setUp() {
		final var value = List.of("ABC", "DEF");
		when(mockAnnotation.value()).thenReturn(value.toArray(new String[0]));
	}

	@Test
	void allowedValue() {
		validator.initialize(mockAnnotation);

		assertThat(validator.isValid("ABC")).isTrue();
		assertThat(validator.isValid("ABC", mockContext)).isTrue();
		assertThat(validator.isValid("DEF")).isTrue();
		assertThat(validator.isValid("DEF", mockContext)).isTrue();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verifyNoInteractions(mockContext);
	}

	@Test
	void disallowedValue() {
		validator.initialize(mockAnnotation);

		assertThat(validator.isValid("DISALLOWED")).isFalse();
		assertThat(validator.isValid("DISALLOWED", mockContext)).isFalse();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockContext).addMessageParameter(any(String.class), any());
	}

	@Test
	void nullValueWhenNullableIsFalse() {
		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(null)).isFalse();
		assertThat(validator.isValid(null, mockContext)).isFalse();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verify(mockContext).addMessageParameter(any(String.class), any());
	}

	@Test
	void nullValueWhenNullableIsTrue() {
		when(mockAnnotation.nullable()).thenReturn(true);
		validator.initialize(mockAnnotation);

		assertThat(validator.isValid(null)).isTrue();
		assertThat(validator.isValid(null, mockContext)).isTrue();

		verify(mockAnnotation).value();
		verify(mockAnnotation).nullable();
		verifyNoInteractions(mockContext);
	}

	@Test
	void testMessage() {
		validator.initialize(mockAnnotation);
		assertThat(validator.getMessage()).isEqualTo("must be one of: [ABC, DEF]");

		verify(mockAnnotation).value();
	}
}
