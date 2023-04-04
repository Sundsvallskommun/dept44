package se.sundsvall.petinventory.service;

import static org.zalando.problem.Status.NOT_FOUND;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zalando.problem.Problem;

import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.integration.db.PetNameRepository;
import se.sundsvall.petinventory.integration.petstore.PetStoreClient;
import se.sundsvall.petinventory.service.mapper.PetInventoryMapper;

@Service
public class PetInventoryService {

	@Autowired
	private PetStoreClient petStoreClient;

	@Autowired
	private PetNameRepository petNameRepository;

	public PetInventoryItem getPetInventoryItem(final long id) {
		return petStoreClient.findPetById(id)
			.map(PetInventoryMapper::toPetInvetoryItem)
			.map(this::populateWithName)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "No pet found for provided ID"));
	}

	public List<PetInventoryItem> getPetInventoryList() {
		return petStoreClient.findAllPets().stream()
			.map(PetInventoryMapper::toPetInvetoryItem)
			.map(this::populateWithName)
			.toList();
	}

	private PetInventoryItem populateWithName(final PetInventoryItem petInventoryItem) {
		petNameRepository.findById(petInventoryItem.getId()).ifPresent(petNameEntity -> petInventoryItem.setName(petNameEntity.getName()));
		return petInventoryItem;
	}
}
