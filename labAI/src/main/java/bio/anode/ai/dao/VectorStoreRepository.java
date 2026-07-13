package bio.anode.ai.dao;

import bio.anode.ai.dao.jpa.VectorStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VectorStoreRepository extends JpaRepository<VectorStoreEntity, String> {
}
