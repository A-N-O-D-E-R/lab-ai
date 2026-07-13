package bio.anode.ai.controller.model.vectorstores;

import bio.anode.ai.dao.jpa.VectorStoreFileEntity;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** OpenAI vector-store-file association object. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VectorStoreFileResponse(
        String id,
        String object,
        String vectorStoreId,
        long createdAt,
        String status) {

    public static VectorStoreFileResponse from(VectorStoreFileEntity e) {
        // OpenAI uses the file id as the vector-store-file id in responses.
        return new VectorStoreFileResponse(
                e.getFileId(), "vector_store.file", e.getVectorStoreId(), e.getCreatedAt(), e.getStatus());
    }
}
