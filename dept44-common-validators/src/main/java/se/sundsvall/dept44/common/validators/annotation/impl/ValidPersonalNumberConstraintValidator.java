package se.sundsvall.dept44.common.validators.annotation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import se.sundsvall.dept44.common.validators.annotation.ValidPersonalNumber;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

/**
 * Defines the logic to validate that a string is a valid personal number.
 */
public class ValidPersonalNumberConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidPersonalNumber, String> {

	private static final String REGEX_PATTERN = "^(19|20)\\d{10}$";
	private boolean nullable;

	@Override
	public void initialize(final ValidPersonalNumber constraintAnnotation) {
		this.nullable = constraintAnnotation.nullable();
	}

	@Override
	public boolean isValid(final String value, final ConstraintValidatorContext context) {
		if (isNull(value) && nullable) {
			return true;
		}

		return nonNull(value) && value.matches(REGEX_PATTERN);
	}

	@Override
	public boolean isValid(final String value) {
		return isValid(value, null);
	}

	@Override
	public String getMessage() {
		return ofNullable(findMethod(ValidPersonalNumber.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.orElseThrow(createException(ValidPersonalNumber.class.getName()));
	}
}
