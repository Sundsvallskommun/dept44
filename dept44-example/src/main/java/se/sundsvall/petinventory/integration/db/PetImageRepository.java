package se.sundsvall.petinventory.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;
import se.sundsvall.petinventory.integration.db.model.PetImageEntity;

@Transactional
@CircuitBreaker(name = "PetImageRepository")
public interface PetImageRepository extends JpaRepository<PetImageEntity, Long>, JpaSpecificationExecutor<PetImageEntity> {

	List<PetImageEntity> findByPetNameId(long id);
}
