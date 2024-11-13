package se.sundsvall.dept44.common.validators.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import se.sundsvall.dept44.common.validators.annotation.impl.ValidBase64ConstraintValidator;

/**
 * The annotated element must be properly BASE64-encoded according to RFC 4648. Accepts CharSequence.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4648">RFC 4648</a>
 */
@Documented
@Target({
	ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidBase64ConstraintValidator.class)
public @interface ValidBase64 {

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	String message() default "not a valid BASE64-encoded string";

	/**
	 * Controls whether the value can be null or not.
	 *
	 * If set to true, the validator will accept the value as valid when null.
	 * If set to false (default), the validator will reject the value as invalid when null.
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
