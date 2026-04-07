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
 * final var relation = Relation.create("CONNECTED",
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
