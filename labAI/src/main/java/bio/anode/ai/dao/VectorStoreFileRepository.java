package bio.anode.ai.dao;

import bio.anode.ai.dao.jpa.VectorStoreFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VectorStoreFileRepository extends JpaRepository<VectorStoreFileEntity, String> {

    List<VectorStoreFileEntity> findByVectorStoreId(String vectorStoreId);

    Optional<VectorStoreFileEntity> findByVectorStoreIdAndFileId(String vectorStoreId, String fileId);

    long countByVectorStoreId(String vectorStoreId);

    void deleteByVectorStoreId(String vectorStoreId);
}
