package se.sundsvall.petinventory.service.mapper;

import static java.util.Objects.isNull;

import org.apache.commons.lang3.ObjectUtils;

import generated.swagger.io.petstore.Pet;
import se.sundsvall.petinventory.api.model.PetInventoryItem;

public class PetInventoryMapper {

	private PetInventoryMapper() {}

	public static PetInventoryItem toPetInvetoryItem(final Pet pet) {
		if (isNull(pet)) {
			return null;
		}
		return PetInventoryItem.create()
			.withId(pet.getId())
			.withPrice(pet.getPrice())
			.withType(ObjectUtils.defaultIfNull(pet.getType(), "UNKNOWN").toString());
	}
}
