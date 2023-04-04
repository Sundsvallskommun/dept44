package se.sundsvall.petinventory.integration.petstore;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.petinventory.integration.petstore.configuration.PetStoreConfiguration.CLIENT_ID;

import java.util.List;
import java.util.Optional;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import generated.swagger.io.petstore.Pet;
import se.sundsvall.petinventory.integration.petstore.configuration.PetStoreConfiguration;

@FeignClient(name = CLIENT_ID, url = "${integration.petstore.url}", configuration = PetStoreConfiguration.class)
public interface PetStoreClient {

	/**
	 * Fetch all pets in the pet store.
	 *
	 * @return all available pets.
	 */
	@GetMapping(path = "/pets", produces = APPLICATION_JSON_VALUE)
	List<Pet> findAllPets();

	/**
	 * Fetch pet by ID.
	 *
	 * @param petId the ID of the pet.
	 * @return the pet that matches the provided ID.
	 */
	@GetMapping(path = "/pets/{petId}", produces = APPLICATION_JSON_VALUE)
	Optional<Pet> findPetById(@PathVariable(value = "petId") long petId);
}
