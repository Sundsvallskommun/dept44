package se.sundsvall.petinventory.api;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.web.util.UriComponentsBuilder.fromPath;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.zalando.problem.Problem;
import org.zalando.problem.violations.ConstraintViolationProblem;
import se.sundsvall.petinventory.api.model.PetInventoryItem;
import se.sundsvall.petinventory.service.PetInventoryService;

@RestController
@Validated
@RequestMapping("/pet-inventory-items")
@Tag(name = "Pet inventory", description = "Pet inventory operations")
public class PetInventoryResource {

	private static final String CONTENT_DISPOSITION_HEADER_VALUE = "attachment; filename=\"%s\"";

	private final PetInventoryService petInventoryService;

	public PetInventoryResource(final PetInventoryService petInventoryService) {
		this.petInventoryService = petInventoryService;
	}

	@GetMapping(produces = {
		APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE
	})
	@Operation(summary = "Get Pet inventory items", responses = {
		@ApiResponse(responseCode = "200", description = "Successful operation", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
			Problem.class, ConstraintViolationProblem.class
		}))),
		@ApiResponse(responseCode = "500", description = "Internal Server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "502", description = "Bad Gateway", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<List<PetInventoryItem>> getPetInventoryList() {
		return ok(petInventoryService.getPetInventoryList());
	}

	@GetMapping(path = "/{id}", produces = {
		APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE
	})
	@Operation(summary = "Get Pet inventory item by id", responses = {
		@ApiResponse(responseCode = "200", description = "Successful operation", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
			Problem.class, ConstraintViolationProblem.class
		}))),
		@ApiResponse(responseCode = "500", description = "Internal Server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "502", description = "Bad Gateway", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<PetInventoryItem> getPetInventoryItem(@PathVariable(name = "id") final long id) {
		return ok(petInventoryService.getPetInventoryItem(id));
	}

	@PostMapping(path = "/{id}/images", consumes = {
		MULTIPART_FORM_DATA_VALUE
	}, produces = {
		APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE
	})
	@Operation(summary = "Add pet inventory item image by id", responses = {
		@ApiResponse(responseCode = "201", description = "Successful operation", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
			Problem.class, ConstraintViolationProblem.class
		}))),
		@ApiResponse(responseCode = "500", description = "Internal Server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "502", description = "Bad Gateway", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<Void> addPetImage(@PathVariable(name = "id") final long id, @RequestPart("file") final MultipartFile multipartFile) {
		final var petImageId = petInventoryService.savePetImage(id, multipartFile);
		return created(fromPath("/pet-inventory-items/{id}/images/{pictureId}").buildAndExpand(id, petImageId).toUri())
			.header(CONTENT_TYPE, ALL_VALUE)
			.build();
	}

	@GetMapping(path = "/{id}/images/{imageId}", produces = {
		MediaType.ALL_VALUE
	})
	@Operation(summary = "Get pet inventory item image by id", responses = {
		@ApiResponse(responseCode = "201", description = "Successful operation", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
			Problem.class, ConstraintViolationProblem.class
		}))),
		@ApiResponse(responseCode = "500", description = "Internal Server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "502", description = "Bad Gateway", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<byte[]> getPetImage(@PathVariable(name = "id") final long id, @PathVariable(name = "imageId") final long imageId) {
		final var petImage = petInventoryService.getPetImage(id, imageId);
		return ok()
			.header(CONTENT_DISPOSITION, CONTENT_DISPOSITION_HEADER_VALUE.formatted(petImage.getFileName()))
			.contentLength(petImage.getContent().length)
			.contentType(MediaType.parseMediaType(petImage.getMimeType()))
			.body(petImage.getContent());
	}
}
