package se.sundsvall.dept44.models.api.paging.validation.impl;

import jakarta.persistence.Column;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.internal.engine.messageinterpolation.util.InterpolationHelper;
import se.sundsvall.dept44.models.api.paging.AbstractParameterPagingAndSortingBase;
import se.sundsvall.dept44.models.api.paging.validation.ValidSortByProperty;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static org.springframework.util.CollectionUtils.isEmpty;

public class ValidSortByPropertyConstraintValidator implements ConstraintValidator<ValidSortByProperty, AbstractParameterPagingAndSortingBase> {

	private static final String CUSTOM_ERROR_MESSAGE_TEMPLATE = "One or more of the sortBy properties %s are not valid. Valid properties to sort by are %s.";
	private final Set<String> entityProperties = new HashSet<>();


	@Override
	public void initialize(ValidSortByProperty constraintAnnotation) {
		entityProperties.addAll(Stream.of(constraintAnnotation.value().getDeclaredFields())
			.filter(field -> field.isAnnotationPresent(Column.class))
			.map(Field::getName)
			.filter(name -> Arrays.stream(constraintAnnotation.exclude()).noneMatch(name::equals))
			.collect(collectingAndThen(toList(), list -> {
				list.addAll(Arrays.asList(constraintAnnotation.include()));
				return list;
			})));
	}

	@Override
	public boolean isValid(final AbstractParameterPagingAndSortingBase parameters, final ConstraintValidatorContext context) {
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
