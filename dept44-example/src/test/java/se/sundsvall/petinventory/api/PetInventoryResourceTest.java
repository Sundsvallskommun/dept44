package se.sundsvall.petinventory.api;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.petinventory.Application;
import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;
import se.sundsvall.petinventory.service.PetInventoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class PetInventoryResourceTest {

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@MockitoBean
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
	void postPetImage() throws Exception {

		// Arrange
		final var id = 1L;
		final var imageId = 666L;

		final var file = new MockMultipartFile("file", "dept44.jpg", MediaType.IMAGE_JPEG_VALUE,
			new ClassPathResource("files/dept44.jpg").getInputStream());

		when(petInventoryServiceMock.savePetImage(anyLong(), any(MultipartFile.class))).thenReturn(imageId);

		final MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

		// Act
		mockMvc.perform(multipart("/pet-inventory-items/{id}/images", id).file(file))
			.andExpect(status().isCreated())
			.andExpect(header().string("Content-Type", "*/*"))
			.andExpect(header().string("Location", "/pet-inventory-items/" + id + "/images/" + imageId));

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
