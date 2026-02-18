package se.sundsvall.dept44.common.validators.annotation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorContextImpl;
import se.sundsvall.dept44.common.validators.annotation.OneOf;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

/**
 * Defines the logic to validate that a string exists in a list of allowed values.
 */
public class OneOfConstraintValidator extends AbstractValidator implements ConstraintValidator<OneOf, String> {

	private boolean nullable;
	private List<String> allowedValues;

	@Override
	public void initialize(final OneOf annotation) {
		nullable = annotation.nullable();
		allowedValues = Arrays.stream(annotation.value()).toList();
	}

	@Override
	public boolean isValid(final String value, final ConstraintValidatorContext context) {
		if (isNull(value) && nullable) {
			return true;
		}

		if (!allowedValues.contains(value)) {
			if (!isNull(context)) {
				((ConstraintValidatorContextImpl) context).addMessageParameter("allowedValues", allowedValues);
			}
			return false;
		}

		return true;
	}

	@Override
	boolean isValid(final String value) {
		return isValid(value, null);
	}

	@Override
	String getMessage() {
		return ofNullable(findMethod(OneOf.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.map(message -> message.replace("{allowedValues}", String.valueOf(allowedValues)))
			.orElseThrow(createException(OneOf.class.getName()));
	}
}
