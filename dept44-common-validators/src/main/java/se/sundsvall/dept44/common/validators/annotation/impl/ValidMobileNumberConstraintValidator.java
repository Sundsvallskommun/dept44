package se.sundsvall.dept44.common.validators.annotation.impl;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import se.sundsvall.dept44.common.validators.annotation.ValidMobileNumber;

/**
 * Defines the logic to validate that a string is a valid mobile number.
 */
public class ValidMobileNumberConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidMobileNumber, String> {

	private static final String REGEX_PATTERN = "^07[02369]\\d{7}$";
	private boolean nullable;

	@Override
	public void initialize(final ValidMobileNumber constraintAnnotation) {
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
		return ofNullable(findMethod(ValidMobileNumber.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.orElseThrow(createException(ValidMobileNumber.class.getName()));
	}
}
