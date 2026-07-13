package bio.anode.ai.dao;

import bio.anode.ai.dao.jpa.ModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelRepository extends JpaRepository<ModelEntity, String> {
}
