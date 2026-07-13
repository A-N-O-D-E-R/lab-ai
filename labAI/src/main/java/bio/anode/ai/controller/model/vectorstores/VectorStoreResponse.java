package bio.anode.ai.controller.model.vectorstores;

import bio.anode.ai.dao.jpa.VectorStoreEntity;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** OpenAI vector store object. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VectorStoreResponse(
        String id,
        String object,
        String name,
        long createdAt,
        String status,
        FileCounts fileCounts) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FileCounts(int inProgress, int completed, int failed, int cancelled, int total) {
    }

    public static VectorStoreResponse from(VectorStoreEntity e) {
        return new VectorStoreResponse(
                e.getId(), "vector_store", e.getName(), e.getCreatedAt(), e.getStatus(),
                new FileCounts(e.getFileCountInProgress(), e.getFileCountCompleted(),
                        e.getFileCountFailed(), 0, e.getFileCountTotal()));
    }
}
