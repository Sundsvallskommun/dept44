package se.sundsvall.dept44.common.validators.annotation.impl;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.util.ReflectionUtils.findMethod;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import java.util.Base64;
import se.sundsvall.dept44.common.validators.annotation.ValidBase64;

/**
 * Defines the logic to validate that a string is a valid non-blank base64-string.
 */
public class ValidBase64ConstraintValidator extends AbstractValidator implements ConstraintValidator<ValidBase64, String> {

	private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

	private boolean nullable;

	@Override
	public void initialize(final ValidBase64 constraintAnnotation) {
		this.nullable = constraintAnnotation.nullable();
	}

	@Override
	public boolean isValid(final String value, final ConstraintValidatorContext context) {
		if (isNull(value) && nullable) {
			return true;
		}
		if (isBlank(value)) {
			return false;
		}
		return isValidBase64(value);
	}

	@Override
	public boolean isValid(final String value) {
		return isValid(value, null);
	}

	@Override
	public String getMessage() {
		return ofNullable(findMethod(ValidBase64.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.orElseThrow(createException(ValidBase64.class.getName()));
	}

	private boolean isValidBase64(final String value) {
		try {
			BASE64_DECODER.decode(value);
		} catch (final Exception e) {
			return false;
		}

		return true;
	}
}
