package se.sundsvall.dept44.models.api.paging.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import se.sundsvall.dept44.models.api.paging.validation.impl.ValidSortByPropertyConstraintValidator;

/**
 * Validates that sortBy properties of {@link se.sundsvall.dept44.models.api.paging.AbstractParameterPagingBase} is
 * present in entity class as a {@link jakarta.persistence.Column} i.e. it is possible to sort on.
 */
@Documented
@Target({
	ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidSortByPropertyConstraintValidator.class)
public @interface ValidSortByProperty {

	String message() default "one or more of properties in list are not present in entity.";

	Class<?> value() default Void.class;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	String[] exclude() default {};

	String[] include() default {};
}
