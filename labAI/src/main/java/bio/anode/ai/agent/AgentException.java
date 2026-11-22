package bio.anode.ai.agent;

import org.springframework.http.HttpStatus;

/** An agent run did not produce a usable result. */
public class AgentException extends RuntimeException {

    public enum Reason {
        FAILED(HttpStatus.BAD_GATEWAY, "agent_error"),
        TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "agent_timeout"),
        CANCELLED(HttpStatus.CONFLICT, "agent_cancelled");

        public final HttpStatus status;
        public final String type;

        Reason(HttpStatus status, String type) {
            this.status = status;
            this.type = type;
        }
    }

    private final Reason reason;

    public AgentException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
