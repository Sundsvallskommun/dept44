package se.sundsvall.petinventory.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PetSchedulerWorkerTest {

	@InjectMocks
	private PetSchedulerWorker petSchedulerWorker;

	@Test
	void testGetPets() {
		// Act
		final var result = petSchedulerWorker.getPets();
		// Assert
		assertThat(result).isEmpty();
	}

}
