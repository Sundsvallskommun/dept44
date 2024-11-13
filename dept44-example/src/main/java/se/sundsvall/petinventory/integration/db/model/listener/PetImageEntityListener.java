package se.sundsvall.petinventory.integration.db.model.listener;

import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.MILLIS;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.ZoneId;
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
