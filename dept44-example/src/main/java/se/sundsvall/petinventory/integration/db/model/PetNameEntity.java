package se.sundsvall.petinventory.integration.db.model;

import java.time.OffsetDateTime;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import se.sundsvall.petinventory.integration.db.model.listener.PetNameEntityListener;

@Entity
@Table(name = "pet_name",
	indexes = {
		@Index(name = "pet_name_name_index", columnList = "name")
	})
@EntityListeners(PetNameEntityListener.class)
public class PetNameEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "name")
	private String name;

	@Column(name = "created")
	private OffsetDateTime created;

	@Column(name = "modified")
	private OffsetDateTime modified;

	public static PetNameEntity create() {
		return new PetNameEntity();
	}

	public Long getId() {
		return id;
	}

	public void setId(final Long id) {
		this.id = id;
	}

	public PetNameEntity withId(final Long id) {
		this.id = id;
		return this;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public PetNameEntity withName(final String name) {
		this.name = name;
		return this;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	public PetNameEntity withCreated(final OffsetDateTime created) {
		this.created = created;
		return this;
	}

	public OffsetDateTime getModified() {
		return modified;
	}

	public void setModified(final OffsetDateTime modified) {
		this.modified = modified;
	}

	public PetNameEntity withModified(final OffsetDateTime modified) {
		this.modified = modified;
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(created, id, modified, name);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PetNameEntity other)) {
			return false;
		}
		return Objects.equals(created, other.created) && Objects.equals(id, other.id) && Objects.equals(modified, other.modified) && Objects.equals(name, other.name);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("PetNameEntity [id=").append(id).append(", name=").append(name).append(", created=").append(created).append(", modified=").append(modified).append("]");
		return builder.toString();
	}
}
