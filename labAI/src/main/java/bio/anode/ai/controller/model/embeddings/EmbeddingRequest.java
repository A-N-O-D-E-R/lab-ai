package bio.anode.ai.controller.model.embeddings;

import tools.jackson.databind.JsonNode;

/**
 * OpenAI embeddings request. {@code input} may be a string, an array of strings, or token arrays;
 * it is captured as a raw node and normalized in the service.
 */
public record EmbeddingRequest(
        JsonNode input,
        String model,
        String encodingFormat,
        Integer dimensions,
        String user) {
}
