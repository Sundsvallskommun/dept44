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

	@Schema(description = "Client ID", example = "joe01doe")
	private String clientId;

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

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public PetInventoryItem withClientId(String clientId) {
		this.clientId = clientId;
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
		return Objects.hash(clientId, id, images, name, price, type);
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
		PetInventoryItem other = (PetInventoryItem) obj;
		return Objects.equals(clientId, other.clientId) && Objects.equals(id, other.id) && Objects.equals(images, other.images) && Objects.equals(name, other.name) && Objects.equals(price, other.price) && Objects.equals(type, other.type);
	}

	@Override
	public String toString() {
		return "PetInventoryItem [id=" + id + ", price=" + price + ", name=" + name + ", type=" + type + ", clientId=" + clientId + ", images=" + images + "]";
	}
}
