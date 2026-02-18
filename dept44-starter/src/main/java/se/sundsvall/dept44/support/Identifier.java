package se.sundsvall.dept44.support;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasText;
import static se.sundsvall.dept44.support.Identifier.Type.CUSTOM;

/**
 * Class for managing identifier metadata in a thread-local context.
 * <p>
 * This class represents an identifier consisting of a {@link Type}, a type string,
 * and a value. It provides functionality to store, retrieve and remove an identifier instance
 * specific to the current thread, making it suitable for use in request-scoped contexts like HTTP request processing.
 * <p>
 * The class also includes parsing logic to convert a string representation of an identifier
 * into an {@link Identifier} object.
 *
 * <p>
 * <strong>Example usage:</strong>
 * 
 * <pre>{@code
 * final var identifier = Identifier.parse("joe001doe; type=adAccount");
 *
 * Identifier.set(identifier);
 *
 * // Retrieve it later in the same thread
 * final var currentIdentifier = Identifier.get();
 *
 * // Clean up when done
 * Identifier.remove();
 * }</pre>
 *
 * <p>
 * Thread safety is handled internally via {@link ThreadLocal}, so each thread
 * has its own isolated {@link Identifier} instance.
 */
public class Identifier {

	public static final String HEADER_NAME = "X-Sent-By";

	private static final ThreadLocal<Identifier> THREAD_LOCAL_INSTANCE = new ThreadLocal<>();
	private static final String TYPE_FORMAT_PREFIX = "type=";
	private static final String HEADER_VALUE_FORMAT = "%s; type=%s";

	private Type type;
	private String typeString;
	private String value;

	public static Identifier create() {
		return new Identifier();
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
		this.typeString = Optional.ofNullable(type).map(t -> UPPER_UNDERSCORE.to(LOWER_CAMEL, t.name())).orElse(null);
	}

	public Identifier withType(Type type) {
		setType(type);
		return this;
	}

	public String getTypeString() {
		return typeString;
	}

	public void setTypeString(String typeString) {
		this.typeString = typeString;
	}

	public Identifier withTypeString(String typeString) {
		setTypeString(typeString);
		return this;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Identifier withValue(String value) {
		setValue(value);
		return this;
	}

	/**
	 * Sets the given {@link Identifier} instance into the thread-local context.
	 *
	 * @param identifier the {@link Identifier} instance to set; if {@code null}, the thread-local value is not modified
	 */
	public static void set(Identifier identifier) {
		Optional.ofNullable(identifier).ifPresent(THREAD_LOCAL_INSTANCE::set);
	}

	/**
	 * Retrieves the current {@link Identifier} instance stored in the thread-local context.
	 *
	 * @return the current {@link Identifier} instance, or {@code null} if none is set
	 */
	public static Identifier get() {
		return THREAD_LOCAL_INSTANCE.get();
	}

	/**
	 * Removes the current {@link Identifier} instance from the thread-local context.
	 */
	public static void remove() {
		THREAD_LOCAL_INSTANCE.remove();
	}

	/**
	 * Parses a {@link String} into an {@link Identifier} object.
	 * <p>
	 * The input string is expected to be in the format:
	 * {@code type=TYPE; VALUE}, where {@code TYPE} is a string representing
	 * the type of the identifier {@link Type}, and {@code VALUE} is the identifier's value.
	 * Whitespace is ignored. The order of the parts (separated by semicolons) is ignored.
	 * <p>
	 * Examples:
	 * 
	 * <pre>{@code
	 * "joe01doe; type=adAccount"
	 * "e9f1319d-0aae-4fc4-bc31-91eb39e02fb5; type=partyId"
	 * "xyz; type=someCustomType"
	 * }</pre>
	 * 
	 * @param  value the string to parse, may be {@code null} or blank
	 * @return       an {@link Identifier} instance if parsing succeeds and required fields are set,
	 *               or {@code null} if the input is invalid or incomplete
	 */
	public static Identifier parse(String value) {

		if (!hasText(value)) {
			return null;
		}

		final var parts = value.replaceAll("\\s+", "").split(";");
		if (parts.length != 2) {
			return null;
		}

		final var identifier = Identifier.create();
		for (String part : parts) {
			if (part.toLowerCase().startsWith(TYPE_FORMAT_PREFIX)) {
				final var parsedType = part.substring(TYPE_FORMAT_PREFIX.length());
				final var type = Type.fromString(parsedType);

				identifier.setType(type != null ? type : CUSTOM);
				identifier.setTypeString(type != null ? UPPER_UNDERSCORE.to(LOWER_CAMEL, type.name()) : parsedType);

				continue;
			}

			identifier.setValue(part);
		}

		return isValid(identifier) ? identifier : null;
	}

	/**
	 * Returns the value as used when transmitted as a HTTP-header.
	 * 
	 * Example:
	 * 
	 * <pre>{@code
	 * "joe01doe; type=adAccount"
	 * "e9f1319d-0aae-4fc4-bc31-91eb39e02fb5; type=partyId"
	 * "xyz; type=someCustomType"
	 * }</pre>
	 * 
	 * @return the value as used in a HTTP-header
	 */
	public String toHeaderValue() {
		if (!isValid(this)) {
			return null;
		}

		return HEADER_VALUE_FORMAT.formatted(
			ofNullable(getValue()).orElse(""),
			ofNullable(getTypeString()).orElse(""));
	}

	private static boolean isValid(Identifier identifier) {
		return hasText(identifier.getValue()) && nonNull(identifier.getType()) && hasText(identifier.getTypeString());
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, typeString, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Identifier other = (Identifier) obj;
		return type == other.type && Objects.equals(typeString, other.typeString) && Objects.equals(value, other.value);
	}

	@Override
	public String toString() {
		return "Identifier [type=" + type + ", typeString=" + typeString + ", value=" + value + "]";
	}

	public enum Type {
		PARTY_ID,
		AD_ACCOUNT,
		CUSTOM;

		public static Type fromString(String value) {
			try {
				return Type.valueOf(LOWER_CAMEL.to(UPPER_UNDERSCORE, value));
			} catch (Exception e) {
				return null;
			}
		}
	}
}
