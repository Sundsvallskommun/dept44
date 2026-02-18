package se.sundsvall.petinventory.service.scheduler;

import java.util.List;
import org.springframework.stereotype.Component;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

import static java.util.Collections.emptyList;

@Component
public class PetSchedulerWorker {

	public List<PetNameEntity> getPets() {
		return emptyList();
	}
}
