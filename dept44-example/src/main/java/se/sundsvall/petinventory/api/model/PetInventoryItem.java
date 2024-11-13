package se.sundsvall.petinventory.api.model;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

@Schema(description = "Pet inventory item model")
public class PetInventoryItem {

	@Schema(description = "Pet ID", example = "1")
	private Long id;

	@Schema(description = "Pet price", example = "1.50")
	private Float price;

	@Schema(description = "Pet name", example = "Daisy")
	private String name;

	@Schema(description = "Pet type", example = "DOG")
	private String type;

	@ArraySchema(schema = @Schema(implementation = PetImage.class))
	private List<PetImage> images;

	public static PetInventoryItem create() {
		return new PetInventoryItem();
	}

	public Long getId() {
		return id;
	}

	public void setId(final Long id) {
		this.id = id;
	}

	public PetInventoryItem withId(final Long id) {
		this.id = id;
		return this;
	}

	public Float getPrice() {
		return price;
	}

	public void setPrice(final Float price) {
		this.price = price;
	}

	public PetInventoryItem withPrice(final Float price) {
		this.price = price;
		return this;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public PetInventoryItem withName(final String name) {
		this.name = name;
		return this;
	}

	public String getType() {
		return type;
	}

	public void setType(final String type) {
		this.type = type;
	}

	public PetInventoryItem withType(final String type) {
		this.type = type;
		return this;
	}

	public List<PetImage> getImages() {
		return images;
	}

	public void setImages(List<PetImage> images) {
		this.images = images;
	}

	public PetInventoryItem withImages(final List<PetImage> images) {
		this.images = images;
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, images, name, price, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) { return true; }
		if (!(obj instanceof final PetInventoryItem other)) { return false; }
		return Objects.equals(id, other.id) && Objects.equals(images, other.images) && Objects.equals(name, other.name) && Objects.equals(price, other.price) && Objects.equals(type, other.type);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("PetInventoryItem [id=").append(id).append(", price=").append(price).append(", name=").append(name).append(", type=").append(type).append(", images=").append(images).append("]");
		return builder.toString();
	}
}
