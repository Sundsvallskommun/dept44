package se.sundsvall.dept44.api.models.paging.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import se.sundsvall.dept44.api.models.paging.validation.impl.MaxPagingLimitConstraintValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxPagingLimitConstraintValidator.class)
public @interface MaxPagingLimit  {
	String message() default "Page limit exceeded";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
