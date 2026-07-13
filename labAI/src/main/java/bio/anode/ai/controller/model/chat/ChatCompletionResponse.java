package bio.anode.ai.controller.model.chat;

import java.util.List;

public record ChatCompletionResponse(String id, String object, String model, long created, List<Choice> choices, Usage usage,
        String systemFingerprint) {

    public ChatCompletionResponse(String id, String model, List<Choice> choices, Usage usage, boolean isStream) {
        this(id, isStream ? "chat.completion.chunk" : "chat.completion", model, System.currentTimeMillis() / 1_000L, choices, usage, "fp");
    }

    public record Choice(String finishReason, int index, Message message, Message delta) {
        public Choice(String finishReason, int index, Message message) {
            this(finishReason, index, message, null);
        }
    }

    public record Message(String role, String content, List<ToolCall> toolCalls) {
    }

    public record ToolCall(String id, String type, ToolCallFunction function) {
    }

    public record ToolCallFunction(String name, String arguments) {
    }

    public record Usage(Integer promptTokens, Integer completionTokens, int totalTokens) {
    }
}
