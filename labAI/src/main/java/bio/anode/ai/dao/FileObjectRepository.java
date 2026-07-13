package bio.anode.ai.dao;

import bio.anode.ai.dao.jpa.FileObjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileObjectRepository extends JpaRepository<FileObjectEntity, String> {
}
