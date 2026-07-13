package bio.anode.ai.controller.model.responses;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** OpenAI Responses API create request (partial; the endpoint is a 501 stub). */
public record ResponsesRequest(
        String model,
        JsonNode input,
        String instructions,
        Boolean stream,
        Double temperature,
        JsonNode tools,
        List<String> include,
        String previousResponseId) {
}
