package se.sundsvall.dept44.common.validators.annotation.impl;

import jakarta.validation.constraints.NotNull;
import java.util.function.Supplier;
import se.sundsvall.dept44.common.validators.annotation.exception.IncompatibleAnnotationException;

import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;

abstract class AbstractValidator {

	AbstractValidator() {}

	static final String MESSAGE_METHOD_NAME = "message";

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	abstract String getMessage();

	/**
	 * Verify if the provided string validates.
	 *
	 * @param  value the value to validate.
	 * @return       true if valid, false otherwise.
	 */
	abstract boolean isValid(final String value);

	static final Supplier<IncompatibleAnnotationException> createException(@NotNull final String className) {
		return () -> new IncompatibleAnnotationException(String.format("%s does not contain method %s()",
			requireNonNull(className, "className may not be null"), MESSAGE_METHOD_NAME));
	}
}
