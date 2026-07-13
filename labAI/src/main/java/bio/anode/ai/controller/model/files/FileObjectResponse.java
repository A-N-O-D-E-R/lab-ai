package bio.anode.ai.controller.model.files;

import bio.anode.ai.dao.jpa.FileObjectEntity;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** OpenAI File object. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FileObjectResponse(
        String id,
        String object,
        long bytes,
        long createdAt,
        String filename,
        String purpose,
        String status) {

    public static FileObjectResponse from(FileObjectEntity e) {
        return new FileObjectResponse(
                e.getId(), "file", e.getBytesSize(), e.getCreatedAt(),
                e.getFilename(), e.getPurpose(), e.getStatus());
    }
}
