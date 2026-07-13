package bio.anode.ai.controller.model.chat;

import org.springframework.core.io.Resource;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Map;

/**
 * OpenAI chat completion request. Only the fields the gateway consumes are bound; unknown OpenAI
 * fields (frequency_penalty, presence_penalty, top_p, n, ...) are accepted and ignored.
 */
public record ChatCompletionRequest(
        String user,
        Map<String, String> metadata,
        @JsonDeserialize(using = ChatMessageDeserializer.class) List<ChatMessage> messages,
        String model,
        Boolean stream,
        String stop) {

    /** {@code stream} is optional in the OpenAI spec; default to non-streaming when absent. */
    public boolean isStream() {
        return Boolean.TRUE.equals(stream);
    }

    public record ChatMessage(String role, String name, String content, List<ContentMedia> medias) {
    }

    public record ContentMedia(String contentType, Resource content) {
    }
}
