package se.sundsvall.petinventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.ALL;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import se.sundsvall.petinventory.Application;
import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;
import se.sundsvall.petinventory.service.PetInventoryService;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("junit")
class PetInventoryResourceTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private PetInventoryService petInventoryServiceMock;

	@Test
	void getPetInventoryList() {

		// Arrange
		when(petInventoryServiceMock.getPetInventoryList()).thenReturn(List.of(PetInventoryItem.create()));

		// Act
		final var response = webTestClient.get().uri("/pet-inventory-items")
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBodyList(PetInventoryItem.class)
			.returnResult()
			.getResponseBody();

		// Assert.
		assertThat(response).hasSize(1);
		verify(petInventoryServiceMock).getPetInventoryList();
		verify(petInventoryServiceMock, never()).getPetInventoryItem(anyLong());
	}

	@Test
	void getPetInventoryItem() {

		// Arrange
		final var id = 1;

		when(petInventoryServiceMock.getPetInventoryItem(id)).thenReturn(PetInventoryItem.create());

		// Act
		final var response = webTestClient.get().uri("/pet-inventory-items/{id}", id)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBody(PetInventoryItem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();

		verify(petInventoryServiceMock).getPetInventoryItem(id);
		verify(petInventoryServiceMock, never()).getPetInventoryList();
	}

	@Test
	void postPetImage() {

		// Arrange
		final var id = 1L;
		final var imageId = 666L;

		final MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
		multipartBodyBuilder
			.part("file", new ClassPathResource("files/dept44.jpg"))
			.contentType(MediaType.MULTIPART_FORM_DATA);

		when(petInventoryServiceMock.savePetImage(anyLong(), any(MultipartFile.class))).thenReturn(imageId);

		// Act
		webTestClient.post().uri("/pet-inventory-items/{id}/images", id)
			.contentType(MULTIPART_FORM_DATA)
			.body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
			.exchange()
			.expectStatus().isCreated()
			.expectHeader().contentType(ALL)
			.expectHeader().location("/pet-inventory-items/" + id + "/images/" + imageId);

		// Assert
		verify(petInventoryServiceMock).savePetImage(eq(id), any(MultipartFile.class));
		verify(petInventoryServiceMock, never()).getPetInventoryList();
	}

	@Test
	void getPetImage() throws IOException {

		// Arrange
		final var classPathResource = new ClassPathResource("files/dept44.jpg");
		final var mimeType = "image/jpeg";
		final var id = 1L;
		final var petImageId = 10L;
		final var petImageEntity = PetImageEntity.create()
			.withContent(classPathResource.getContentAsByteArray())
			.withFileName(classPathResource.getFile().getName())
			.withMimeType(mimeType);

		when(petInventoryServiceMock.getPetImage(anyLong(), anyLong())).thenReturn(petImageEntity);

		// Act
		final var response = webTestClient.get().uri("/pet-inventory-items/{id}/images/{petImageId}", id, petImageId)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(mimeType)
			.expectBody()
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isEqualTo(classPathResource.getContentAsByteArray());
		verify(petInventoryServiceMock).getPetImage(id, petImageId);
	}
}
