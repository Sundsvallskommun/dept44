package se.sundsvall.petinventory.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;
import se.sundsvall.petinventory.integration.db.model.PetNameEntity;

@Transactional
@CircuitBreaker(name = "PetNameRepository")
public interface PetNameRepository extends JpaRepository<PetNameEntity, Long>, JpaSpecificationExecutor<PetNameEntity> {

	Optional<PetNameEntity> findByName(String name);
}
