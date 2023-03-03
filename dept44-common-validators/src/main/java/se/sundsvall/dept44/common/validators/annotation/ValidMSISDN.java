package se.sundsvall.dept44.common.validators.annotation;

import se.sundsvall.dept44.common.validators.annotation.impl.ValidMobileNumberConstraintValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

/**
 * The annotated element must be a valid MSISDN according to the regular expression ^\+[1-9]{1}[0-9]{3,14}$. Accepts
 * CharSequence.
 */
@Documented
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidMobileNumberConstraintValidator.class)
public @interface ValidMSISDN {

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	String message() default "must be a valid MSISDN, regular expression ^\\+[1-9]{1}[0-9]{3,14}$";

	/**
	 * Controls whether the value can be null or not.
	 * <p>
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
