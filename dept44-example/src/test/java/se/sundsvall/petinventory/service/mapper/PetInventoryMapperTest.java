package se.sundsvall.petinventory.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import generated.swagger.io.petstore.Pet;
import generated.swagger.io.petstore.TypeEnum;

class PetInventoryMapperTest {

	@Test
	void toPetInvetoryItem() {

		// Setup
		final var id = 1L;
		final var price = 2.5F;
		final var type = "BIRD";
		final var pet = new Pet()
			.id(id)
			.price(price)
			.type(TypeEnum.fromValue(type));

		// Call
		final var result = PetInventoryMapper.toPetInvetoryItem(pet);

		// Verification
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getName()).isNull(); // Not set in PetInventoryMapper
		assertThat(result.getPrice()).isEqualTo(price);
		assertThat(result.getType()).isEqualTo(type);
	}

	@Test
	void toPetInvetoryItemWhenPetIsNull() {

		// Call
		final var result = PetInventoryMapper.toPetInvetoryItem(null);

		// Verification
		assertThat(result).isNull();
	}
}
