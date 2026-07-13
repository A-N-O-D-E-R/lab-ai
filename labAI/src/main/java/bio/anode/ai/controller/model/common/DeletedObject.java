package bio.anode.ai.controller.model.common;

/** OpenAI deletion result: {@code {"id":..., "object":..., "deleted":true}}. */
public record DeletedObject(String id, String object, boolean deleted) {
}
