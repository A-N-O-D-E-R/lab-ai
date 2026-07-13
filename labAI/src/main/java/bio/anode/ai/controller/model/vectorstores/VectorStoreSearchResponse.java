package bio.anode.ai.controller.model.vectorstores;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** OpenAI vector-store search results page. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VectorStoreSearchResponse(
        String object,
        String searchQuery,
        List<Result> data) {

    public VectorStoreSearchResponse(String searchQuery, List<Result> data) {
        this("vector_store.search_results.page", searchQuery, data);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(String fileId, String filename, Double score, List<Content> content) {
    }

    public record Content(String type, String text) {
        public Content(String text) {
            this("text", text);
        }
    }
}
