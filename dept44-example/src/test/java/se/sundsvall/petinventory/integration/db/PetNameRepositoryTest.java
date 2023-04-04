package se.sundsvall.petinventory.integration.db;

import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

/**
 * PetNameRepository tests
 *
 * @see /src/test/resources/db/testdata-junit.sql for data setup.
 */
@SpringBootTest
@ActiveProfiles("junit")
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/testdata-junit.sql"
})
class PetNameRepositoryTest {

	@Autowired
	private PetNameRepository repository;

	@Test
	void create() {

		final var entity = PetNameEntity.create().withName("Rocco");

		final var result = repository.save(entity);

		// Verification
		assertThat(result).isNotNull();
		assertThat(result.getId()).isPositive();
		assertThat(result.getName()).isEqualTo("Rocco");
		assertThat(result.getCreated()).isCloseTo(now(), within(2, SECONDS));
		assertThat(result.getModified()).isNull();
	}

	@Test
	void findById() {

		// Setup
		final var id = 1L;
		final var name = "Daisy";

		final var result = repository.findById(id).orElseThrow();

		// Verification
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getName()).isEqualTo(name);
	}

	@Test
	void findByIdNotFound() {
		assertThat(repository.findById(123456789L)).isNotPresent();
	}

	@Test
	void findByName() {

		// Setup
		final var id = 2L;
		final var name = "Beatle";

		final var result = repository.findByName(name).orElseThrow();

		// Verification
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getName()).isEqualTo(name);
	}

	@Test
	void findByNameNotFound() {
		assertThat(repository.findByName("NoName")).isNotPresent();
	}

	@Test
	void update() {

		// Setup
		final var id = 3L;

		// Fetch existing entity.
		final var petname3 = repository.findById(id).orElseThrow();
		assertThat(petname3.getName()).isEqualTo("Boozer");
		assertThat(petname3.getModified()).isNull();

		// Update entity.
		petname3.setName("Shaggy");
		repository.save(petname3);

		// Verification
		final var result = repository.findById(id).orElseThrow();
		assertThat(result.getName()).isEqualTo("Shaggy");
		assertThat(result.getModified()).isCloseTo(now(), within(2, SECONDS));
	}

	@Test
	void deleteById() {

		// Setup
		final var id = 4L;

		assertThat(repository.findById(id)).isPresent();

		repository.deleteById(id);

		// Verification
		assertThat(repository.findById(id)).isNotPresent();
	}
}
