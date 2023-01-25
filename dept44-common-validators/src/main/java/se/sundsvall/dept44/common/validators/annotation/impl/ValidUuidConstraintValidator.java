package se.sundsvall.dept44.common.validators.annotation.impl;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

import java.lang.reflect.Method;
import java.util.UUID;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import se.sundsvall.dept44.common.validators.annotation.ValidUuid;

public class ValidUuidConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidUuid, String> {

	private boolean nullable;

	@Override
	public void initialize(ValidUuid constraintAnnotation) {
		this.nullable = constraintAnnotation.nullable();
	}

	@Override
	public boolean isValid(final String value, final ConstraintValidatorContext context) {
		if (isNull(value) && nullable) {
			return true;
		}
		return isValidUUID(value);
	}

	@Override
	public boolean isValid(final String value) {
		return isValid(value, null);
	}

	@Override
	public String getMessage() {
		return ofNullable(findMethod(ValidUuid.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.orElseThrow(createException(ValidUuid.class.getName()));
	}

	private boolean isValidUUID(String value) {
		try {
			UUID.fromString(String.valueOf(value));
		} catch (Exception e) {
			return false;
		}

		return true;
	}
}
