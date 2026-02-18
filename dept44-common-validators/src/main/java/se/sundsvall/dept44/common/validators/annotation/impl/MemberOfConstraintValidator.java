package se.sundsvall.dept44.common.validators.annotation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import se.sundsvall.dept44.common.validators.annotation.MemberOf;
import se.sundsvall.dept44.common.validators.annotation.OneOf;

import static java.util.Comparator.nullsLast;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static org.springframework.util.ReflectionUtils.findMethod;

/**
 * Defines the logic to validate that a string matches a member of a given enum class.
 */
public class MemberOfConstraintValidator extends AbstractValidator implements ConstraintValidator<MemberOf, String> {

	private static final Supplier<Set<String>> CASE_SENSITIVE = HashSet::new;
	private static final Supplier<Set<String>> CASE_INSENSITIVE = () -> new TreeSet<>(nullsLast(String.CASE_INSENSITIVE_ORDER));

	private boolean nullable;
	private boolean caseSensitive;
	private Set<String> allowedValues;

	@Override
	public void initialize(final MemberOf annotation) {
		nullable = annotation.nullable();
		caseSensitive = annotation.caseSensitive();
		allowedValues = Arrays.stream(annotation.value().getEnumConstants())
			.map(Enum::name)
			.collect(toCollection(caseSensitive ? CASE_SENSITIVE : CASE_INSENSITIVE));
	}

	@Override
	public boolean isValid(final String value, final ConstraintValidatorContext context) {
		if (isNull(value) && nullable) {
			return true;
		}

		if (!allowedValues.contains(value)) {
			ofNullable(context)
				.map(ctx -> ctx.unwrap(HibernateConstraintValidatorContext.class))
				.ifPresent(ctx -> {
					ctx.addMessageParameter("allowedValues", allowedValues);
					ctx.addMessageParameter("caseSensitivity", caseSensitive ? "" : " (case-insensitive)");
				});

			return false;
		}

		return true;
	}

	@Override
	boolean isValid(final String value) {
		return isValid(value, null);
	}

	@Override
	String getMessage() {
		return ofNullable(findMethod(OneOf.class, MESSAGE_METHOD_NAME))
			.map(Method::getDefaultValue)
			.map(Object::toString)
			.map(message -> message
				.replace("{allowedValues}", String.valueOf(allowedValues))
				.replace("{caseSensitivity}", caseSensitive ? "" : " (case-insensitive)"))
			.orElseThrow(createException(OneOf.class.getName()));
	}
}
