package bio.anode.ai.controller.model.images;

public record ImageGenerationRequest(
        String prompt,
        String model,
        Integer n,
        String size,
        String quality,
        String style,
        String responseFormat,
        String user) {
}
