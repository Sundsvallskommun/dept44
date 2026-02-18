package se.sundsvall.dept44.common.validators.annotation.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.common.validators.annotation.ValidNamespace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidNamespaceConstraintValidatorTest {

	@Mock
	private ValidNamespace annotationMock;

	@InjectMocks
	private ValidNamespaceConstraintValidator validator;

	@Test
	void validNamespace() {

		// Mock
		validator.initialize(annotationMock);

		// Simple alphanumeric
		assertThat(validator.isValid("mynamespace")).isTrue();
		assertThat(validator.isValid("namespace123")).isTrue();
		assertThat(validator.isValid("NAMESPACE")).isTrue();
		assertThat(validator.isValid("ab")).isTrue();
		assertThat(validator.isValid("12")).isTrue();
		assertThat(validator.isValid("abc123XYZ")).isTrue();

		// With hyphens
		assertThat(validator.isValid("my-namespace")).isTrue();
		assertThat(validator.isValid("hyphen-")).isTrue();
		assertThat(validator.isValid("-hyphen")).isTrue();
		assertThat(validator.isValid("multiple-hyphens-here")).isTrue();

		// With underscores
		assertThat(validator.isValid("my_namespace")).isTrue();
		assertThat(validator.isValid("_underscore")).isTrue();
		assertThat(validator.isValid("underscore_")).isTrue();
		assertThat(validator.isValid("multiple_underscores_here")).isTrue();

		// With pipes
		assertThat(validator.isValid("my|namespace")).isTrue();
		assertThat(validator.isValid("pipe|")).isTrue();
		assertThat(validator.isValid("|pipe")).isTrue();
		assertThat(validator.isValid("multiple|pipes|here")).isTrue();

		// Mixed combinations
		assertThat(validator.isValid("my-namespace_123")).isTrue();
		assertThat(validator.isValid("namespace-with|pipe")).isTrue();
		assertThat(validator.isValid("complex-name_with|all_chars123")).isTrue();
		assertThat(validator.isValid("a-b_c|d")).isTrue();

		// Exactly 32 characters (max length)
		assertThat(validator.isValid("12345678901234567890123456789012")).isTrue();
		assertThat(validator.isValid("a-long-namespace-with-32-chars-")).isTrue();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidNamespace() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("not#valid")).isFalse();
		assertThat(validator.isValid("not%valid", null)).isFalse();
		assertThat(validator.isValid("not valid")).isFalse();
		assertThat(validator.isValid("not@valid")).isFalse();
		assertThat(validator.isValid("not!valid")).isFalse();
		assertThat(validator.isValid("not$valid")).isFalse();
		assertThat(validator.isValid("not&valid")).isFalse();
		assertThat(validator.isValid("not*valid")).isFalse();
		assertThat(validator.isValid("not(valid")).isFalse();
		assertThat(validator.isValid("not)valid")).isFalse();
		assertThat(validator.isValid("not+valid")).isFalse();
		assertThat(validator.isValid("not=valid")).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidNamespaceWithBrackets() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("not[valid")).isFalse();
		assertThat(validator.isValid("not]valid")).isFalse();
		assertThat(validator.isValid("not{valid")).isFalse();
		assertThat(validator.isValid("not}valid")).isFalse();
		assertThat(validator.isValid("not:valid")).isFalse();
		assertThat(validator.isValid("not;valid")).isFalse();
		assertThat(validator.isValid("not'valid")).isFalse();
		assertThat(validator.isValid("not\"valid")).isFalse();
		assertThat(validator.isValid("not<valid")).isFalse();
		assertThat(validator.isValid("not>valid")).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidNamespaceWithPunctuationAndPaths() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("not,valid")).isFalse();
		assertThat(validator.isValid("not.valid")).isFalse();
		assertThat(validator.isValid("not?valid")).isFalse();
		assertThat(validator.isValid("not/valid")).isFalse();
		assertThat(validator.isValid("not\\valid")).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidNamespaceWithWhitespace() {

		validator.initialize(annotationMock);

		assertThat(validator.isValid("")).isFalse();
		assertThat(validator.isValid(" ")).isFalse();
		assertThat(validator.isValid("\t")).isFalse();
		assertThat(validator.isValid("\n")).isFalse();
		assertThat(validator.isValid("  ")).isFalse();
		assertThat(validator.isValid("space at start")).isFalse();
		assertThat(validator.isValid("space at end ")).isFalse();

		verify(annotationMock).nullable();
	}

	@Test
	void invalidNamespaceWithLengthConstraints() {

		validator.initialize(annotationMock);

		// Too short (less than 2 characters)
		assertThat(validator.isValid("a")).isFalse();
		assertThat(validator.isValid("1")).isFalse();
		assertThat(validator.isValid("-")).isFalse();
		assertThat(validator.isValid("_")).isFalse();
		assertThat(validator.isValid("|")).isFalse();

		// Too long (more than 32 characters)
		assertThat(validator.isValid("123456789012345678901234567890123")).isFalse(); // 33 chars
		assertThat(validator.isValid("this-is-a-very-long-namespace-that-exceeds-the-maximum-length")).isFalse();
		assertThat(validator.isValid("aaaaaaaaaabbbbbbbbbbccccccccccdddd")).isFalse(); // 34 chars

		verify(annotationMock).nullable();
	}

	@Test
	void nullNamespace() {

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
		assertThat(validator.isValid("not$valid")).isFalse(); // non-null and invalid values are still treated as invalid.
		assertThat(validator.isValid("not^valid", null)).isFalse(); // non-null and invalid values are still treated as invalid.

		verify(annotationMock).nullable();
	}

	@Test
	void testMessage() {
		assertThat(validator.getMessage()).isEqualTo("not a valid namespace. Must be 2-32 characters and can only contain A-Z, a-z, 0-9, - and _");

		verifyNoInteractions(annotationMock);
	}

}
