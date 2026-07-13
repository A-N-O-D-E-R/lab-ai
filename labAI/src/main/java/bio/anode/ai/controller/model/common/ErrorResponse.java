package bio.anode.ai.controller.model.common;

/**
 * OpenAI-shaped error envelope: {@code {"error": {"message", "type", "param", "code"}}}.
 */
public record ErrorResponse(Error error) {

    public record Error(String message, String type, String param, String code) {
    }

    public static ErrorResponse of(String message, String type, String code) {
        return new ErrorResponse(new Error(message, type, null, code));
    }
}
