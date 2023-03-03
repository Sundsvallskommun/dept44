package se.sundsvall.dept44.common.validators.annotation.impl;

import se.sundsvall.dept44.common.validators.annotation.ValidMSISDN;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

/**
 * Defines the logic to validate that a string is a valid mobile number.
 */
public class ValidMSISDNConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidMSISDN, String> {

	private static final String REGEX_PATTERN = "^\\+[1-9]{1}[0-9]{3,14}$";
	private boolean nullable;

	@Override
	public void initialize(final ValidMSISDN constraintAnnotation) {
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
		return ofNullable(findMethod(ValidMSISDN.class, MESSAGE_METHOD_NAME))
				.map(Method::getDefaultValue)
				.map(Object::toString)
				.orElseThrow(createException(ValidMSISDN.class.getName()));
	}
}