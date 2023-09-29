package se.sundsvall.dept44.models.api.paging.validation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;
import se.sundsvall.dept44.models.api.paging.validation.MaxPagingLimit;

public class MaxPagingLimitConstraintValidator implements ConstraintValidator<MaxPagingLimit, Integer> {

	private static final String MSG = "Page limit cannot be greater than %s";

	@Value("${dept44.models.api.paging.max.limit:100}")
	private int maxLimit;

	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		var isValid = value <= maxLimit;

		if(!isValid) {
			useCustomMessageForValidation(context, String.format(MSG, maxLimit));
		}

		return isValid;

	}

	private void useCustomMessageForValidation(ConstraintValidatorContext constraintContext, String message) {
		constraintContext.disableDefaultConstraintViolation();
		constraintContext.buildConstraintViolationWithTemplate(message).addConstraintViolation();
	}
}
