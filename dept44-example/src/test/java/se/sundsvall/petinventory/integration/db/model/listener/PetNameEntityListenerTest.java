package se.sundsvall.petinventory.integration.db.model.listener;

import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

class PetNameEntityListenerTest {

	@Test
	void prePerist() {

		// Setup
		final var listener = new PetNameEntityListener();
		final var entity = new PetNameEntity();

		// Call
		listener.prePersist(entity);

		// Assertions
		assertThat(entity).hasAllNullFieldsOrPropertiesExcept("id", "created");
		assertThat(entity.getCreated()).isCloseTo(now(), within(2, SECONDS));
	}

	@Test
	void preUpdate() {

		// Setup
		final var listener = new PetNameEntityListener();
		final var entity = new PetNameEntity();

		// Call
		listener.preUpdate(entity);

		// Assertions
		assertThat(entity).hasAllNullFieldsOrPropertiesExcept("id", "modified");
		assertThat(entity.getModified()).isCloseTo(now(), within(2, SECONDS));
	}
}
