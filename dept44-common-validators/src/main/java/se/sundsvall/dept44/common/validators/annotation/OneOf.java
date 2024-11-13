package se.sundsvall.dept44.common.validators.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import se.sundsvall.dept44.common.validators.annotation.impl.OneOfConstraintValidator;

/**
 * The annotated element must exist within a given list of allowed values.
 */
@Documented
@Target({
	ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OneOfConstraintValidator.class)
public @interface OneOf {

	String message() default "must be one of: {allowedValues}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	String[] value();

	boolean nullable() default false;
}
