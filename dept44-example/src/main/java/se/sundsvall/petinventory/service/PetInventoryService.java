package se.sundsvall.petinventory.service;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.zalando.problem.Status.NOT_FOUND;
import static se.sundsvall.petinventory.service.mapper.PetInventoryMapper.toPetImages;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.integration.db.PetImageRepository;
import se.sundsvall.petinventory.integration.db.PetNameRepository;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;
import se.sundsvall.petinventory.integration.petstore.PetStoreClient;
import se.sundsvall.petinventory.service.mapper.PetInventoryMapper;

@Service
public class PetInventoryService {

	private static final String ERROR_MESSAGE_PET_NOT_FOUND = "No pet found for provided id!";
	private static final String ERROR_MESSAGE_IMAGE_NOT_FOUND = "No pet image found for provided petImageId!";

	@Autowired
	private PetStoreClient petStoreClient;

	@Autowired
	private PetNameRepository petNameRepository;

	@Autowired
	private PetImageRepository petImageRepository;

	public PetInventoryItem getPetInventoryItem(final long id) {
		return petStoreClient.findPetById(id)
			.map(PetInventoryMapper::toPetInventoryItem)
			.map(this::populateWithName)
			.map(this::populateWithImages)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, ERROR_MESSAGE_PET_NOT_FOUND));
	}

	public List<PetInventoryItem> getPetInventoryList() {
		return petStoreClient.findAllPets().stream()
			.map(PetInventoryMapper::toPetInventoryItem)
			.map(this::populateWithName)
			.map(this::populateWithImages)
			.toList();
	}

	public long savePetImage(long petInventoryId, MultipartFile file) {

		final var petNameEntity = petNameRepository.findById(petInventoryId)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, ERROR_MESSAGE_PET_NOT_FOUND));

		try {
			final var petImageEntity = petImageRepository.save(PetImageEntity.create()
				.withContent(file.getBytes())
				.withFileName(file.getOriginalFilename())
				.withMimeType(file.getContentType())
				.withPetName(petNameEntity));

			return petImageEntity.getId();
		} catch (final Exception e) {
			throw Problem.valueOf(Status.INTERNAL_SERVER_ERROR, "Could not store image!");
		}
	}

	public PetImageEntity getPetImage(long id, long petImageId) {

		final var petNameEntity = petNameRepository.findById(id)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, ERROR_MESSAGE_PET_NOT_FOUND));

		return petNameEntity.getImages().stream()
			.filter(petImage -> Objects.equals(petImageId, petImage.getId()))
			.findFirst()
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, ERROR_MESSAGE_IMAGE_NOT_FOUND));
	}

	private PetInventoryItem populateWithName(final PetInventoryItem petInventoryItem) {
		petNameRepository.findById(petInventoryItem.getId()).ifPresent(petNameEntity -> petInventoryItem.setName(petNameEntity.getName()));
		return petInventoryItem;
	}

	private PetInventoryItem populateWithImages(final PetInventoryItem petInventoryItem) {
		final var images = toPetImages(petImageRepository.findByPetNameId(petInventoryItem.getId()));
		if (isNotEmpty(images)) {
			petInventoryItem.setImages(images);
		}
		return petInventoryItem;
	}
}
