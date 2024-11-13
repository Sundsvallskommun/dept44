package se.sundsvall.dept44.common.validators.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import se.sundsvall.dept44.common.validators.annotation.impl.ValidMunicipalityIdConstraintValidator;

/**
 * The annotated element must be a valid municipality id according to the regular expression ^d{4}$. Accepts
 * CharSequence.
 *
 * @see <a href="https://sundsvall.atlassian.net/wiki/spaces/SK/pages/22675457/OpenAPI+namns+ttning">Open API
 *      namnsättning</a>
 */
@Documented
@Target({
	ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidMunicipalityIdConstraintValidator.class)
public @interface ValidMunicipalityId {

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	String message() default "not a valid municipality ID";

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
