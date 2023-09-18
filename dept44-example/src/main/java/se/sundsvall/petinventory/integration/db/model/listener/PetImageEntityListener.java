package se.sundsvall.petinventory.integration.db.model.listener;

import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.MILLIS;

import java.time.ZoneId;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;

public class PetImageEntityListener {

	@PrePersist
	void prePersist(final PetImageEntity entity) {
		entity.setCreated(now(ZoneId.systemDefault()).truncatedTo(MILLIS));
	}

	@PreUpdate
	void preUpdate(final PetImageEntity entity) {
		entity.setModified(now(ZoneId.systemDefault()).truncatedTo(MILLIS));
	}
}
