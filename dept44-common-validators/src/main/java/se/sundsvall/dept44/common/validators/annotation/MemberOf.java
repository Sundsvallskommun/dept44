package se.sundsvall.dept44.common.validators.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import se.sundsvall.dept44.common.validators.annotation.impl.MemberOfConstraintValidator;

/**
 * The annotated element must be equal to a member of a given enum class, optionally without regard to
 * case-sensitivity.
 */
@Documented
@Target({
	ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MemberOfConstraintValidator.class)
public @interface MemberOf {

	String message() default "must be one of: {allowedValues}{caseSensitivity}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	Class<? extends Enum<?>> value();

	boolean nullable() default false;

	boolean caseSensitive() default true;
}
