package bio.anode.ai.controller.model.embeddings;

import java.util.List;

public record EmbeddingResponse(String object, List<EmbeddingData> data, String model, Usage usage) {

    public EmbeddingResponse(List<EmbeddingData> data, String model, Usage usage) {
        this("list", data, model, usage);
    }

    public record EmbeddingData(String object, float[] embedding, int index) {
        public EmbeddingData(float[] embedding, int index) {
            this("embedding", embedding, index);
        }
    }

    public record Usage(int promptTokens, int totalTokens) {
    }
}
