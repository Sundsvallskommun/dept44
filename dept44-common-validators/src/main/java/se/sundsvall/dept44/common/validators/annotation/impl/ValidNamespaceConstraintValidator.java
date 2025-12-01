package se.sundsvall.dept44.common.validators.annotation.impl;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import se.sundsvall.dept44.common.validators.annotation.ValidNamespace;

public class ValidNamespaceConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidNamespace, String> {

	private static final String NAMESPACE_REGEXP = "[\\w|\\-]{2,32}";
	private boolean nullable;

	@Override
	public void initialize(final ValidNamespace constraintAnnotation) {
		this.nullable = constraintAnnotation.nullable();
	}

	@Override
	public boolean isValid(final String value, final ConstraintValidatorContext context) {
		if (isNull(value) && nullable) {
			return true;
		}

		return nonNull(value) && value.matches(NAMESPACE_REGEXP);
	}

	@Override
	public boolean isValid(final String value) {
		return isValid(value, null);
	}

	@Override
	public String getMessage() {
		return ofNullable(findMethod(ValidNamespace.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.orElseThrow(createException(ValidNamespace.class.getName()));
	}

}
