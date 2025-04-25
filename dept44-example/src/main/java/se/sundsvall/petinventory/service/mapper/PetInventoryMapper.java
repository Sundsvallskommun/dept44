package se.sundsvall.petinventory.service.mapper;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import generated.swagger.io.petstore.Pet;
import java.util.List;
import java.util.Optional;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.petinventory.api.model.PetImage;
import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;

public final class PetInventoryMapper {

	private PetInventoryMapper() {}

	public static PetInventoryItem toPetInventoryItem(final Pet pet) {
		return ofNullable(pet)
			.map(p -> PetInventoryItem.create()
				.withId(p.getId())
				.withPrice(p.getPrice())
				.withType(defaultIfNull(p.getType(), "UNKNOWN").toString())
				.withClientId(Optional.ofNullable(Identifier.get()).map(Identifier::getValue).orElse(null)))
			.orElse(null);
	}

	public static List<PetImage> toPetImages(final List<PetImageEntity> petImageEntityList) {
		return ofNullable(petImageEntityList).orElse(emptyList()).stream()
			.map(PetInventoryMapper::toPetImage)
			.toList();
	}

	public static PetImage toPetImage(final PetImageEntity petImageEntity) {
		return ofNullable(petImageEntity)
			.map(p -> PetImage.create()
				.withId(p.getId())
				.withFileName(p.getFileName())
				.withMimeType(p.getMimeType()))
			.orElse(null);
	}
}
