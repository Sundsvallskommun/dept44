package se.sundsvall.petinventory.service.scheduler;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetSchedulerTest {

	@Mock
	private PetSchedulerWorker petSchedulerWorkerMock;

	@InjectMocks
	private PetScheduler petScheduler;

	@Test
	void testGetAndProcessPets() {
		// Arrange
		final PetNameEntity pet1 = PetNameEntity.create()
			.withName("Pet1").withId(1L)
			.withCreated(OffsetDateTime.now().minusDays(2))
			.withModified(OffsetDateTime.now());
		final PetNameEntity pet2 = PetNameEntity.create()
			.withName("Pet2")
			.withId(2L)
			.withCreated(OffsetDateTime.now().minusDays(1))
			.withModified(OffsetDateTime.now().minusHours(1));
		when(petSchedulerWorkerMock.getPets()).thenReturn(List.of(pet1, pet2));

		// Act
		petScheduler.getAndProcessPets();

		// Assert
		verify(petSchedulerWorkerMock).getPets();
		verifyNoMoreInteractions(petSchedulerWorkerMock);
	}
}
