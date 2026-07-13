package bio.anode.ai.controller.model.moderations;

import tools.jackson.databind.JsonNode;

/** OpenAI {@code POST /moderations} request. {@code input} may be a string or array of strings. */
public record ModerationRequest(JsonNode input, String model) {
}
