package se.sundsvall.dept44.support;

import java.util.Objects;
import java.util.Optional;

import static se.sundsvall.dept44.support.Relation.ResourceIdentifier.parseIdentifier;

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

	private static final String SECTION_DELIMITER = "|";
	private static final String FIELD_DELIMITER = ";";
	private static final String INVALID_RELATION_FORMAT_MESSAGE = "Invalid relation format, expected 3 sections separated by '%s' (e.g. 'LINK|1234;case;caseservice;MY_NAMESPACE|98c7b451-a14a-4f9f-91da-8834ba01eb81;asset;assetservice;OTHER_NAMESPACE') but got: %s";
	private static final String INVALID_RESOURCE_IDENTIFIER_FORMAT_MESSAGE = "Invalid resource identifier format, expected 4 fields separated by '%s' (e.g '1234;case;caseservice;MY_NAMESPACE') but got: %s";
	private String type;
	private ResourceIdentifier source;
	private ResourceIdentifier target;

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
		if (source == null && target == null) {
			return null;
		}
		return String.join(SECTION_DELIMITER, nullToEmpty(type), formatIdentifier(source), formatIdentifier(target));
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
		final var sections = value.split(String.format("\\%s", SECTION_DELIMITER), -1);
		if (sections.length != 3) {
			throw new IllegalArgumentException(String.format(INVALID_RELATION_FORMAT_MESSAGE, SECTION_DELIMITER, value));
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
	 * which together uniquely identify a resource. Type and service
	 * are always stored in lowercase.
	 */
	public static class ResourceIdentifier {

		private String resourceId;
		private String type;
		private String service;
		private String namespace;

		/**
		 * Convenience factory method that creates a fully populated {@link ResourceIdentifier}.
		 *
		 * @param  resourceId the resource ID
		 * @param  type       the type (will be stored in lowercase)
		 * @param  service    the service (will be stored in lowercase)
		 * @param  namespace  the namespace (maybe {@code null})
		 * @return            a new {@link ResourceIdentifier} instance
		 */
		public static ResourceIdentifier create(String resourceId, String type, String service, String namespace) {
			if (resourceId == null || resourceId.isBlank()) {
				throw new IllegalArgumentException("resourceId must not be null or blank");
			}
			if (type == null || type.isBlank()) {
				throw new IllegalArgumentException("type must not be null or blank");
			}
			if (service == null || service.isBlank()) {
				throw new IllegalArgumentException("service must not be null or blank");
			}

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
				.map(v -> v.replaceAll("[\\s\\-_]+", ""))
				.orElse(null);
		}

		static ResourceIdentifier parseIdentifier(String section) {
			if (section == null || section.isBlank()) {
				return null;
			}
			final var fields = section.split(FIELD_DELIMITER, -1);
			if (fields.length != 4) {
				throw new IllegalArgumentException(String.format(INVALID_RESOURCE_IDENTIFIER_FORMAT_MESSAGE, FIELD_DELIMITER, section));
			}
			return ResourceIdentifier.create(
				fields[0],
				fields[1],
				fields[2],
				emptyToNull(fields[3]));
		}

		private static String emptyToNull(String value) {
			return (value != null && !value.isBlank()) ? value : null;
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
