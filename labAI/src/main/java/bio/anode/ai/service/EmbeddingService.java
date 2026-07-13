package bio.anode.ai.service;

import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.controller.model.embeddings.EmbeddingResponse;
import bio.anode.ai.support.NotImplementedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs {@code POST /api/v1/embeddings}. Uses whichever {@link EmbeddingModel} the configured
 * providers contribute; returns 501 when none is available.
 */
@Service
@Slf4j
public class EmbeddingService {

    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final LabaiProperties properties;

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingModel, LabaiProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    public EmbeddingResponse embed(JsonNode input, String model, Integer dimensions) {
        // getIfUnique (not getIfAvailable) so a future second provider bean doesn't throw here;
        // the knowledge embedding bean is defaultCandidate=false and excluded from this lookup.
        var em = embeddingModel.getIfUnique();
        if (em == null) {
            throw new NotImplementedException("No (unique) embedding provider is configured");
        }

        var inputs = normalizeInput(input);
        var options = EmbeddingOptions.builder()
                .model(properties.getModelId(model))
                .dimensions(dimensions)
                .build();

        var response = em.call(new org.springframework.ai.embedding.EmbeddingRequest(inputs, options));

        var data = new ArrayList<EmbeddingResponse.EmbeddingData>();
        for (var e : response.getResults()) {
            data.add(new EmbeddingResponse.EmbeddingData(e.getOutput(), e.getIndex()));
        }

        var usage = response.getMetadata().getUsage();
        var promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        var totalTokens = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : promptTokens;

        return new EmbeddingResponse(data, model, new EmbeddingResponse.Usage(promptTokens, totalTokens));
    }

    private List<String> normalizeInput(JsonNode input) {
        if (input == null || input.isNull()) {
            throw new IllegalArgumentException("'input' is required");
        }
        var inputs = new ArrayList<String>();
        if (input.isArray()) {
            // Array of strings. (Token-id arrays are not supported by the Spring AI abstraction.)
            for (var node : input) {
                inputs.add(node.asString());
            }
        } else {
            inputs.add(input.asString());
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("'input' must not be empty");
        }
        return inputs;
    }
}
