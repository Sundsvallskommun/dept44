package se.sundsvall.dept44.common.validators.annotation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import se.sundsvall.dept44.common.validators.annotation.ValidUuid;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.ReflectionUtils.findMethod;

/**
 * Defines the logic to validate that a string is a valid UUID.
 */
public class ValidUuidConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidUuid, String> {

	private static final Pattern CANONICAL_UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private boolean nullable;

	@Override
	public void initialize(final ValidUuid constraintAnnotation) {
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

	/**
	 * The canonical 8-4-4-4-12 hexadecimal form only. {@link java.util.UUID#fromString(String)} alone also accepts
	 * shorter groups, such as "1-1-1-1-1".
	 */
	private boolean isValidUUID(final String value) {
		return nonNull(value) && CANONICAL_UUID.matcher(value).matches();
	}
}
