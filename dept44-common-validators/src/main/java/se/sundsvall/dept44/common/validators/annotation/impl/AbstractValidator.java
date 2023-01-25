package se.sundsvall.dept44.common.validators.annotation.impl;

import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;

import java.util.function.Supplier;

import javax.validation.constraints.NotNull;

import se.sundsvall.dept44.common.validators.annotation.exception.IncompatibleAnnotationException;

abstract class AbstractValidator {

	AbstractValidator() {}

	static final String MESSAGE_METHOD_NAME = "message";

	abstract String getMessage();
	
	abstract boolean isValid(final String value);
	
	static final Supplier<IncompatibleAnnotationException> createException(@NotNull String className) {
		return () -> new IncompatibleAnnotationException(String.format("%s does not contain method %s()",
			requireNonNull(className, "className may not be null"), MESSAGE_METHOD_NAME));
	}
}
