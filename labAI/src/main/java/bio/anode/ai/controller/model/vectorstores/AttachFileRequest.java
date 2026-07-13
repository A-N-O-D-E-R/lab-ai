package bio.anode.ai.controller.model.vectorstores;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** OpenAI attach-file-to-vector-store request. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AttachFileRequest(String fileId) {
}
