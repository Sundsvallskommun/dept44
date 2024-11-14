package se.sundsvall.dept44.common.validators.annotation.impl;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import se.sundsvall.dept44.common.validators.annotation.ValidOrganizationNumber;

/**
 * Defines the logic to validate that a string is a valid organization number.
 */
public class ValidOrganizationNumberConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidOrganizationNumber, String> {

	private static final String REGEX_PATTERN = "^([1235789][\\d][2-9]\\d{7})$";
	private boolean nullable;

	@Override
	public void initialize(final ValidOrganizationNumber constraintAnnotation) {
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
		return ofNullable(findMethod(ValidOrganizationNumber.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.orElseThrow(createException(ValidOrganizationNumber.class.getName()));
	}
}
