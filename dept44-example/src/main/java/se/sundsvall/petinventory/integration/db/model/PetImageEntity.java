package se.sundsvall.petinventory.integration.db.model;

import static org.hibernate.Length.LONG32;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import se.sundsvall.petinventory.integration.db.model.listener.PetImageEntityListener;

@Entity
@Table(name = "pet_image")
@EntityListeners(PetImageEntityListener.class)
public class PetImageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "file_name")
	private String fileName;

	@Column(name = "mime_type")
	private String mimeType;

	@Column(name = "content", length = LONG32)
	private byte[] content;

	@Column(name = "created")
	private OffsetDateTime created;

	@Column(name = "modified")
	private OffsetDateTime modified;

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "pet_name_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_pet_image_pet_name_id_pet_name_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private PetNameEntity petName;

	public static PetImageEntity create() {
		return new PetImageEntity();
	}

	public Long getId() {
		return id;
	}

	public void setId(final Long id) {
		this.id = id;
	}

	public PetImageEntity withId(final Long id) {
		this.id = id;
		return this;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public PetImageEntity withFileName(final String fileName) {
		this.fileName = fileName;
		return this;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public PetImageEntity withMimeType(final String mimeType) {
		this.mimeType = mimeType;
		return this;
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(final byte[] content) {
		this.content = content;
	}

	public PetImageEntity withContent(final byte[] content) {
		this.content = content;
		return this;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	public PetImageEntity withCreated(final OffsetDateTime created) {
		this.created = created;
		return this;
	}

	public OffsetDateTime getModified() {
		return modified;
	}

	public void setModified(final OffsetDateTime modified) {
		this.modified = modified;
	}

	public PetImageEntity withModified(final OffsetDateTime modified) {
		this.modified = modified;
		return this;
	}

	public PetNameEntity getPetName() {
		return petName;
	}

	public void setPetName(PetNameEntity petName) {
		this.petName = petName;
	}

	public PetImageEntity withPetName(PetNameEntity petName) {
		this.petName = petName;
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + Arrays.hashCode(content);
		result = (prime * result) + Objects.hash(created, fileName, id, mimeType, modified, petName);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) { return true; }
		if (!(obj instanceof final PetImageEntity other)) { return false; }
		return Arrays.equals(content, other.content) && Objects.equals(created, other.created) && Objects.equals(fileName, other.fileName) && Objects.equals(id, other.id) && Objects.equals(mimeType, other.mimeType) && Objects.equals(modified,
			other.modified) && Objects.equals(petName, other.petName);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("PetImageEntity [id=").append(id).append(", fileName=").append(fileName).append(", mimeType=").append(mimeType).append(", content=").append(Arrays.toString(content)).append(", created=").append(created).append(", modified=").append(
			modified).append(", petName=").append(petName).append("]");
		return builder.toString();
	}
}
