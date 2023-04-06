package se.sundsvall.petinventory.api.model;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

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

	@Override
	public int hashCode() {
		return Objects.hash(id, name, price, type);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof final PetInventoryItem other)) {
			return false;
		}
		return Objects.equals(id, other.id) && Objects.equals(name, other.name) && Objects.equals(price, other.price) && Objects.equals(type, other.type);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("PetInventoryItem [id=").append(id).append(", price=").append(price).append(", name=").append(name).append(", type=").append(type).append("]");
		return builder.toString();
	}
}
