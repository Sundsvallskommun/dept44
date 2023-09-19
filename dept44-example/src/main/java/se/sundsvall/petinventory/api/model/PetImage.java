package se.sundsvall.petinventory.api.model;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pet image model")
public class PetImage {

	@Schema(description = "Pet image ID", example = "1")
	private Long id;

	@Schema(description = "File name", example = "image.jpg")
	private String fileName;

	@Schema(description = "Mime type", example = "image/jpeg")
	private String mimeType;

	public static PetImage create() {
		return new PetImage();
	}

	public Long getId() {
		return id;
	}

	public void setId(final Long id) {
		this.id = id;
	}

	public PetImage withId(final Long id) {
		this.id = id;
		return this;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public PetImage withFileName(final String fileName) {
		this.fileName = fileName;
		return this;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public PetImage withMimeType(final String mimeType) {
		this.mimeType = mimeType;
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileName, id, mimeType);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) { return true; }
		if (!(obj instanceof final PetImage other)) { return false; }
		return Objects.equals(fileName, other.fileName) && Objects.equals(id, other.id) && Objects.equals(mimeType, other.mimeType);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("PetImage [id=").append(id).append(", fileName=").append(fileName).append(", mimeType=").append(mimeType).append("]");
		return builder.toString();
	}
}
