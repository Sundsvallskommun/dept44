package se.sundsvall.petinventory.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
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

	@OneToMany(mappedBy = "petName", fetch = FetchType.EAGER, orphanRemoval = true)
	private List<PetImageEntity> images;

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

	public List<PetImageEntity> getImages() {
		return images;
	}

	public void setImages(final List<PetImageEntity> images) {
		this.images = images;
	}

	public PetNameEntity withImages(final List<PetImageEntity> images) {
		this.images = images;
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(created, id, images, modified, name);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof final PetNameEntity other)) {
			return false;
		}
		return Objects.equals(created, other.created) && Objects.equals(id, other.id) && Objects.equals(images, other.images) && Objects.equals(modified, other.modified) && Objects.equals(name, other.name);
	}

	@Override
	public String toString() {
		return "PetNameEntity [id=" + id + ", name=" + name + ", created=" + created + ", modified=" + modified + ", images=" + images + "]";
	}
}
