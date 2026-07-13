package bio.anode.ai.support;

/**
 * Thrown by endpoints whose OpenAI family is defined (route + DTOs) but not backed by any provider.
 * Mapped to HTTP 501 with an OpenAI-shaped error envelope by {@link GlobalExceptionHandler}.
 */
public class NotImplementedException extends RuntimeException {
    public NotImplementedException(String message) {
        super(message);
    }
}
