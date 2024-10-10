package se.sundsvall.petinventory.integration.db.model.listener;

import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.MILLIS;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.ZoneId;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

public class PetNameEntityListener {

	@PrePersist
	void prePersist(final PetNameEntity entity) {
		entity.setCreated(now(ZoneId.systemDefault()).truncatedTo(MILLIS));
	}

	@PreUpdate
	void preUpdate(final PetNameEntity entity) {
		entity.setModified(now(ZoneId.systemDefault()).truncatedTo(MILLIS));
	}
}
