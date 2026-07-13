package bio.anode.ai.controller.model.vectorstores;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** OpenAI create-vector-store request. {@code file_ids} are attached (ingested) on creation. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateVectorStoreRequest(String name, List<String> fileIds) {
}
