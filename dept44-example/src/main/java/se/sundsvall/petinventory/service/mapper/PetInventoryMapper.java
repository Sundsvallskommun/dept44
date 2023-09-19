package se.sundsvall.petinventory.service.mapper;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import generated.swagger.io.petstore.Pet;
import se.sundsvall.petinventory.api.model.PetImage;
import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;

public class PetInventoryMapper {

	private PetInventoryMapper() {}

	public static PetInventoryItem toPetInventoryItem(final Pet pet) {
		if (isNull(pet)) {
			return null;
		}
		return PetInventoryItem.create()
			.withId(pet.getId())
			.withPrice(pet.getPrice())
			.withType(ObjectUtils.defaultIfNull(pet.getType(), "UNKNOWN").toString());
	}

	public static List<PetImage> toPetImages(final List<PetImageEntity> petImageEntityList) {
		return Optional.ofNullable(petImageEntityList).orElse(emptyList()).stream()
			.map(PetInventoryMapper::toPetImage)
			.toList();
	}

	public static PetImage toPetImage(final PetImageEntity petImageEntity) {
		if (isNull(petImageEntity)) {
			return null;
		}
		return PetImage.create()
			.withId(petImageEntity.getId())
			.withFileName(petImageEntity.getFileName())
			.withMimeType(petImageEntity.getMimeType());
	}
}
