package se.sundsvall.petinventory.service.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

@Component
public class PetScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(PetScheduler.class);
	private final PetSchedulerWorker petSchedulerWorker;

	public PetScheduler(final PetSchedulerWorker petSchedulerWorker) {
		this.petSchedulerWorker = petSchedulerWorker;
	}

	@Dept44Scheduled(name = "${scheduler.pet-scheduler.name}",
		cron = "${scheduler.pet-scheduler.cron}",
		maximumExecutionTime = "${scheduler.pet-scheduler.maximum-execution-time}")
	public void getAndProcessPets() {
		LOG.info("Getting and processing pets");
		petSchedulerWorker.getPets().forEach(pet -> LOG.info("Processing pet: {}", pet));
		LOG.info("Finished getting and processing pets");
	}
}
