package bio.anode.ai.controller.model.vectorstores;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** OpenAI vector-store search request. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VectorStoreSearchRequest(String query, Integer maxNumResults) {
}
