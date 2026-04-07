package se.sundsvall.dept44.support;

import java.util.Objects;
import java.util.Optional;

/**
 * Class representing a relation between a source and a target resource.
 * <p>
 * Each resource is identified by a {@link ResourceIdentifier} consisting of
 * a resource ID, type, service, and namespace. The relation itself has a type
 * which is always stored in uppercase.
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>{@code
 * final var relation = Relation.create("LINK",
 * 	ResourceIdentifier.create("source-id", "case", "my-service", "my-namespace"),
 * 	ResourceIdentifier.create("target-id", "case", "other-service", "other-namespace"));
 * }</pre>
 */
public class Relation {

	private String type;
	private ResourceIdentifier source;
	private ResourceIdentifier target;

	public static Relation create() {
		return new Relation();
	}

	/**
	 * Convenience factory method that creates a fully populated {@link Relation}.
	 *
	 * @param  type   the relation type (will be stored in uppercase)
	 * @param  source the source {@link ResourceIdentifier}
	 * @param  target the target {@link ResourceIdentifier}
	 * @return        a new {@link Relation} instance
	 */
	public static Relation create(String type, ResourceIdentifier source, ResourceIdentifier target) {
		return new Relation()
			.withType(type)
			.withSource(source)
			.withTarget(target);
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = Optional.ofNullable(type).map(String::toUpperCase).orElse(null);
	}

	public Relation withType(String type) {
		setType(type);
		return this;
	}

	public ResourceIdentifier getSource() {
		return source;
	}

	public void setSource(ResourceIdentifier source) {
		this.source = source;
	}

	public Relation withSource(ResourceIdentifier source) {
		setSource(source);
		return this;
	}

	public ResourceIdentifier getTarget() {
		return target;
	}

	public void setTarget(ResourceIdentifier target) {
		this.target = target;
	}

	public Relation withTarget(ResourceIdentifier target) {
		setTarget(target);
		return this;
	}

	private static final String SECTION_DELIMITER = "|";
	private static final String FIELD_DELIMITER = ";";

	/**
	 * Serializes this {@link Relation} to a compact string format suitable for use in
	 * HTTP query parameters or headers.
	 * <p>
	 * Format: {@code {type}|{resourceId};{type};{service};{namespace}|{resourceId};{type};{service};{namespace}}
	 * <p>
	 * Example: {@code LINK|src-id;case;myservice;ns|tgt-id;asset;otherservice;ns2}
	 *
	 * @return the formatted string representation
	 */
	public String formatRelation() {
		if (type == null || (source == null && target == null)) {
			return null;
		}
		return String.join(SECTION_DELIMITER, type, formatIdentifier(source), formatIdentifier(target));
	}

	/**
	 * Parses a compact string format into a {@link Relation}.
	 *
	 * @param  value                    the formatted string
	 * @return                          a new {@link Relation} instance
	 * @throws IllegalArgumentException if the format is invalid
	 * @see                             #formatRelation()
	 */
	public static Relation parseRelation(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Relation format string must not be null or blank");
		}
		final var sections = value.split("\\|", -1);
		if (sections.length != 3) {
			throw new IllegalArgumentException("Invalid relation format, expected 3 sections separated by '|' but got: " + value);
		}
		return Relation.create(sections[0], parseIdentifier(sections[1]), parseIdentifier(sections[2]));
	}

	private static String formatIdentifier(ResourceIdentifier identifier) {
		if (identifier == null) {
			return "";
		}
		return String.join(FIELD_DELIMITER,
			nullToEmpty(identifier.getResourceId()),
			nullToEmpty(identifier.getType()),
			nullToEmpty(identifier.getService()),
			nullToEmpty(identifier.getNamespace()));
	}

	private static String nullToEmpty(String value) {
		return value != null ? value : "";
	}

	private static ResourceIdentifier parseIdentifier(String section) {
		if (section == null || section.isBlank()) {
			return null;
		}
		final var fields = section.split(";", -1);
		if (fields.length != 4) {
			throw new IllegalArgumentException("Invalid resource identifier format, expected 4 fields separated by ';' but got: " + section);
		}
		return ResourceIdentifier.create(
			emptyToNull(fields[0]),
			emptyToNull(fields[1]),
			emptyToNull(fields[2]),
			emptyToNull(fields[3]));
	}

	private static String emptyToNull(String value) {
		return value != null && !value.isEmpty() ? value : null;
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, source, target);
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
		Relation other = (Relation) obj;
		return Objects.equals(type, other.type) && Objects.equals(source, other.source) && Objects.equals(target, other.target);
	}

	@Override
	public String toString() {
		return "Relation [type=" + type + ", source=" + source + ", target=" + target + "]";
	}

	/**
	 * Represents a resource identifier within a relation.
	 * <p>
	 * A resource identifier consists of a resource ID, type, service, and namespace,
	 * which together uniquely identify a resource in the system. Type and service
	 * are always stored in lowercase.
	 */
	public static class ResourceIdentifier {

		private String resourceId;
		private String type;
		private String service;
		private String namespace;

		public static ResourceIdentifier create() {
			return new ResourceIdentifier();
		}

		/**
		 * Convenience factory method that creates a fully populated {@link ResourceIdentifier}.
		 *
		 * @param  resourceId the resource ID
		 * @param  type       the type (will be stored in lowercase)
		 * @param  service    the service (will be stored in lowercase)
		 * @param  namespace  the namespace (may be {@code null})
		 * @return            a new {@link ResourceIdentifier} instance
		 */
		public static ResourceIdentifier create(String resourceId, String type, String service, String namespace) {
			return new ResourceIdentifier()
				.withResourceId(resourceId)
				.withType(type)
				.withService(service)
				.withNamespace(namespace);
		}

		public String getResourceId() {
			return resourceId;
		}

		public void setResourceId(String resourceId) {
			this.resourceId = resourceId;
		}

		public ResourceIdentifier withResourceId(String resourceId) {
			setResourceId(resourceId);
			return this;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = sanitize(type);
		}

		public ResourceIdentifier withType(String type) {
			setType(type);
			return this;
		}

		public String getService() {
			return service;
		}

		public void setService(String service) {
			this.service = sanitize(service);
		}

		public ResourceIdentifier withService(String service) {
			setService(service);
			return this;
		}

		public String getNamespace() {
			return namespace;
		}

		public void setNamespace(String namespace) {
			this.namespace = namespace;
		}

		public ResourceIdentifier withNamespace(String namespace) {
			setNamespace(namespace);
			return this;
		}

		private static String sanitize(String value) {
			return Optional.ofNullable(value)
				.map(String::toLowerCase)
				.map(String::trim)
				.map(v -> v.replace(" ", "").replace("-", "").replace("_", ""))
				.orElse(null);
		}

		@Override
		public int hashCode() {
			return Objects.hash(resourceId, type, service, namespace);
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
			ResourceIdentifier other = (ResourceIdentifier) obj;
			return Objects.equals(resourceId, other.resourceId)
				&& Objects.equals(type, other.type)
				&& Objects.equals(service, other.service)
				&& Objects.equals(namespace, other.namespace);
		}

		@Override
		public String toString() {

			return "ResourceIdentifier [resourceId=" + resourceId + ", type=" + type + ", service=" + service + ", namespace=" + namespace + "]";
		}
	}
}
