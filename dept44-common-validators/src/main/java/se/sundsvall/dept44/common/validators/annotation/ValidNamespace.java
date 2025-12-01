package se.sundsvall.dept44.common.validators.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import se.sundsvall.dept44.common.validators.annotation.impl.ValidNamespaceConstraintValidator;

/**
 * The annotated element must be a valid namespace. A valid namespace must be 2-32 characters long and can only contain
 * the characters A-Z, a-z, 0-9, - and _.
 */
@Documented
@Target({
	ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidNamespaceConstraintValidator.class)
public @interface ValidNamespace {

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	String message() default "not a valid namespace. Must be 2-32 characters and can only contain A-Z, a-z, 0-9, - and _";

	/**
	 * Controls whether the value can be null or not. If set to true, the validator will accept the value as valid when
	 * null. If set to false (default), the validator will reject the value as invalid when null.
	 *
	 * @return true if the value is accepted as nullable, false otherwise.
	 */
	boolean nullable() default false;

	/**
	 * Returns the groups.
	 *
	 * @return the groups.
	 */
	Class<?>[] groups() default {};

	/**
	 * Returns the payload.
	 *
	 * @return the payload.
	 */
	Class<? extends Payload>[] payload() default {};
}
