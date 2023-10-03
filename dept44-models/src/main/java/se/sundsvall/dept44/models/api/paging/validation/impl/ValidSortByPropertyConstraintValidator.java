package se.sundsvall.dept44.models.api.paging.validation.impl;

import static org.springframework.util.CollectionUtils.isEmpty;

import jakarta.persistence.Column;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.internal.engine.messageinterpolation.util.InterpolationHelper;
import se.sundsvall.dept44.models.api.paging.AbstractParameterPagingBase;
import se.sundsvall.dept44.models.api.paging.validation.ValidSortByProperty;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ValidSortByPropertyConstraintValidator implements ConstraintValidator<ValidSortByProperty, AbstractParameterPagingBase> {

	private static final String CUSTOM_ERROR_MESSAGE_TEMPLATE = "One or more of the sortBy properties %s are not valid. Valid properties to sort by are %s.";
	private final Set<String> entityProperties = new HashSet<>();


	@Override
	public void initialize(ValidSortByProperty constraintAnnotation) {
		entityProperties.addAll(Stream.of(constraintAnnotation.value().getDeclaredFields())
			.filter(field -> field.isAnnotationPresent(Column.class))
			.map(Field::getName)
			.filter(name -> Arrays.stream(constraintAnnotation.exclude()).noneMatch(name::equals))
			.toList());
	}

	@Override
	public boolean isValid(final AbstractParameterPagingBase parameters, final ConstraintValidatorContext context) {
		final boolean isValid = isEmpty(parameters.getSortBy()) ||	entityProperties.containsAll(parameters.getSortBy());

		if (!isValid) {
			useCustomMessageForValidation(context, parameters.getSortBy());
		}

		return isValid;
	}

	private void useCustomMessageForValidation(final ConstraintValidatorContext constraintContext, final List<String> sortBy) {
		constraintContext.disableDefaultConstraintViolation();
		constraintContext.buildConstraintViolationWithTemplate(InterpolationHelper.escapeMessageParameter(String.format(CUSTOM_ERROR_MESSAGE_TEMPLATE, sortBy, entityProperties))).addConstraintViolation();
	}
}
