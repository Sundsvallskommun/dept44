package se.sundsvall.petinventory.integration.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

/**
 * PetNameRepository tests
 *
 * @see <a href=
 *      "/src/test/resources/db/scripts/testdata-junit.sql">/src/test/resources/db/scripts/testdata-junit.sql</a> for
 *      data setup.
 */
@SpringBootTest
@ActiveProfiles("junit")
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/testdata-junit.sql"
})
class PetImageRepositoryTest {

	@Autowired
	private PetImageRepository repository;

	@Test
	void create() {

		// Setup
		final var fileName = "test.jpg";
		final var mimeType = "image/jpeg";
		final var content = new byte[] {
			5, 1, 9, 2, 6
		};
		final var entity = PetImageEntity.create()
			.withFileName(fileName)
			.withMimeType(mimeType)
			.withContent(content)
			.withPetName(PetNameEntity.create().withId(1L));

		// Act
		final var result = repository.save(entity);

		// Assert
		assertThat(result).isNotNull();
		assertThat(result.getId()).isPositive();
		assertThat(result.getFileName()).isEqualTo(fileName);
		assertThat(result.getMimeType()).isEqualTo(mimeType);
		assertThat(result.getContent()).isEqualTo(content);
		assertThat(result.getCreated()).isCloseTo(now(), within(2, SECONDS));
		assertThat(result.getModified()).isNull();
	}

	@Test
	void findById() {

		// Setup
		final var id = 101L;

		// Act
		final var result = repository.findById(id).orElseThrow();

		// Assert
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getFileName()).isEqualTo("Daisy.jpg");
	}

	@Test
	void findByIdNotFound() {
		assertThat(repository.findById(123456789L)).isNotPresent();
	}

	@Test
	void findByPetNameId() {

		// Setup
		final var petNameId = 2L;

		// Act
		final var result = repository.findByPetNameId(petNameId);

		// Assert
		assertThat(result)
			.extracting(PetImageEntity::getId, PetImageEntity::getMimeType, PetImageEntity::getFileName)
			.containsExactly(tuple(102L, "image/jpeg", "Beatle.jpg"));
	}

	@Test
	void findByPetNameIdNotFOund() {
		assertThat(repository.findByPetNameId(666)).isEmpty();
	}

	@Test
	void update() {

		// Setup
		final var id = 103L;
		final var newContent = new byte[] {
			5, 1, 9, 2, 6
		};

		// Fetch existing entity.
		final var petImage = repository.findById(id).orElseThrow();
		assertThat(petImage.getPetName().getName()).isEqualTo("Boozer");
		assertThat(petImage.getContent()).hasSize(274);
		assertThat(petImage.getModified()).isNull();

		// Update entity.
		petImage.setContent(newContent);
		repository.save(petImage);

		// Verification
		final var result = repository.findById(id).orElseThrow();
		assertThat(petImage.getPetName().getName()).isEqualTo("Boozer");
		assertThat(petImage.getContent()).hasSize(5);
		assertThat(result.getModified()).isCloseTo(now(), within(2, SECONDS));
	}

	@Test
	void deleteById() {

		// Setup
		final var id = 104L;

		assertThat(repository.findById(id)).isPresent();

		repository.deleteById(id);
		repository.flush();

		// Verification
		assertThat(repository.findById(id)).isNotPresent();
	}
}
