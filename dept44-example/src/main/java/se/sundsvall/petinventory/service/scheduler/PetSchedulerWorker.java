package se.sundsvall.petinventory.service.scheduler;

import static java.util.Collections.emptyList;

import java.util.List;
import org.springframework.stereotype.Component;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

@Component
public class PetSchedulerWorker {

	public List<PetNameEntity> getPets() {
		return emptyList();
	}
}
