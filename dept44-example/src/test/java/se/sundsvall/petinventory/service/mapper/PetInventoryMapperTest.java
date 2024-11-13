package se.sundsvall.petinventory.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import generated.swagger.io.petstore.Pet;
import generated.swagger.io.petstore.TypeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.petinventory.api.model.PetImage;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;

class PetInventoryMapperTest {

	@Test
	void toPetInventoryItem() {

		// Arrange
		final var id = 1L;
		final var price = 2.5F;
		final var type = "BIRD";
		final var pet = new Pet()
			.id(id)
			.price(price)
			.type(TypeEnum.fromValue(type));

		// Act
		final var result = PetInventoryMapper.toPetInventoryItem(pet);

		// Assert
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getName()).isNull(); // Not set in PetInventoryMapper
		assertThat(result.getPrice()).isEqualTo(price);
		assertThat(result.getType()).isEqualTo(type);
	}

	@Test
	void toPetInventoryItemWhenPetIsNull() {

		// Act
		final var result = PetInventoryMapper.toPetInventoryItem(null);

		// Assert
		assertThat(result).isNull();
	}

	@Test
	void toPetImages() {

		// Arrange
		final var id = 1L;
		final var fileName = "test.jpg";
		final var mimeType = "image/jpeg";
		final var petImageEntityList = List.of(PetImageEntity.create()
			.withId(id)
			.withFileName(fileName)
			.withMimeType(mimeType));

		// Act
		final var result = PetInventoryMapper.toPetImages(petImageEntityList);

		// Assert
		assertThat(result)
			.extracting(PetImage::getId, PetImage::getMimeType, PetImage::getFileName)
			.containsExactly(tuple(1L, "image/jpeg", "test.jpg"));
	}
}
