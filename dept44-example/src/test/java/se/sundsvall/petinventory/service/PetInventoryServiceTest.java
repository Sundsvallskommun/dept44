package se.sundsvall.petinventory.service;

import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.zalando.problem.Status.NOT_FOUND;

import generated.swagger.io.petstore.Pet;
import generated.swagger.io.petstore.TypeEnum;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zalando.problem.ThrowableProblem;
import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.integration.db.PetImageRepository;
import se.sundsvall.petinventory.integration.db.PetNameRepository;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;
import se.sundsvall.petinventory.integration.petstore.PetStoreClient;

@ExtendWith(MockitoExtension.class)
class PetInventoryServiceTest {

	@Mock
	private PetNameRepository petNameRepositoryMock;

	@Mock
	private PetImageRepository petImageRepositoryMock;

	@Mock
	private PetStoreClient petStoreClientMock;

	@InjectMocks
	private PetInventoryService service;

	@Test
	void getPetInventoryItem() {

		// Setup
		final var id = 1L;
		final var name = "Pluto";
		final var price = 50.75f;
		final var type = "DOG";

		when(petNameRepositoryMock.findById(id)).thenReturn(Optional.of(PetNameEntity.create().withName(name)));
		when(petStoreClientMock.findPetById(id)).thenReturn(Optional.of(new Pet().id(id).price(price).type(TypeEnum
			.fromValue(type))));

		// Call
		final var result = service.getPetInventoryItem(id);

		// Verifications
		verify(petNameRepositoryMock).findById(id);
		verify(petStoreClientMock).findPetById(id);

		// Assertions
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getName()).isEqualTo(name);
		assertThat(result.getPrice()).isEqualTo(price);
		assertThat(result.getType()).isEqualTo(type);
	}

	@Test
	void getPetInventoryItemNotFound() {

		// Setup
		final var id = 1L;

		when(petStoreClientMock.findPetById(id)).thenReturn(empty());

		// Call
		final var result = assertThrows(ThrowableProblem.class, () -> service.getPetInventoryItem(id));

		// Verifications
		verify(petStoreClientMock).findPetById(id);
		verify(petNameRepositoryMock, never()).findById(id);

		// Assertions
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(NOT_FOUND);
		assertThat(result.getMessage()).isEqualTo("Not Found: No pet found for provided id!");
	}

	@Test
	void getPetInventoryList() {

		// Setup
		final var id1 = 1L;
		final var id2 = 2L;
		final var id3 = 3L;
		final var name1 = "name1";
		final var name2 = "name2";
		final var name3 = "name3";
		final var price1 = 1.1f;
		final var price2 = 2.2f;
		final var price3 = 3.31f;
		final var type1 = "DOG";
		final var type2 = "CAT";
		final var type3 = "BIRD";

		when(petNameRepositoryMock.findById(id1)).thenReturn(Optional.of(PetNameEntity.create().withName(name1)));
		when(petNameRepositoryMock.findById(id2)).thenReturn(Optional.of(PetNameEntity.create().withName(name2)));
		when(petNameRepositoryMock.findById(id3)).thenReturn(Optional.of(PetNameEntity.create().withName(name3)));
		when(petStoreClientMock.findAllPets()).thenReturn(List.of(
			new Pet().id(id1).price(price1).type(TypeEnum.fromValue(type1)),
			new Pet().id(id2).price(price2).type(TypeEnum.fromValue(type2)),
			new Pet().id(id3).price(price3).type(TypeEnum.fromValue(type3))));

		// Call
		final var result = service.getPetInventoryList();

		// Verifications
		verify(petNameRepositoryMock).findById(id1);
		verify(petNameRepositoryMock).findById(id2);
		verify(petNameRepositoryMock).findById(id3);
		verify(petStoreClientMock).findAllPets();

		// Assertions
		assertThat(result)
			.extracting(PetInventoryItem::getId, PetInventoryItem::getName, PetInventoryItem::getPrice,
				PetInventoryItem::getType)
			.containsExactlyInAnyOrder(
				tuple(id1, name1, price1, type1),
				tuple(id2, name2, price2, type2),
				tuple(id3, name3, price3, type3));
	}
}
