package se.sundsvall.petinventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import se.sundsvall.petinventory.Application;
import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.service.PetInventoryService;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("junit")
class PetInventoryResourceTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private PetInventoryService petInventoryServiceMock;

	@LocalServerPort
	private int port;

	@Test
	void getPetInventoryList() {

		// Setup.
		when(petInventoryServiceMock.getPetInventoryList()).thenReturn(List.of(PetInventoryItem.create()));

		// Call.
		final var response = webTestClient.get().uri("/pet-inventory-items/")
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBodyList(PetInventoryItem.class)
			.returnResult()
			.getResponseBody();

		// Verification.
		assertThat(response).hasSize(1);

		verify(petInventoryServiceMock).getPetInventoryList();
		verify(petInventoryServiceMock, never()).getPetInventoryItem(anyLong());
	}

	@Test
	void getPetInventoryItem() {

		// Setup
		final var id = 1;

		when(petInventoryServiceMock.getPetInventoryItem(id)).thenReturn(PetInventoryItem.create());

		// Call.
		final var response = webTestClient.get().uri("/pet-inventory-items/{id}", id)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBody(PetInventoryItem.class)
			.returnResult()
			.getResponseBody();

		// Verification.
		assertThat(response).isNotNull();

		verify(petInventoryServiceMock).getPetInventoryItem(id);
		verify(petInventoryServiceMock, never()).getPetInventoryList();
	}
}
