package bio.anode.ai.agent;

import java.math.BigDecimal;

/** Successful result of one agent run, free of provider-specific types. */
public record AgentOutcome(String text, String providerSessionId, BigDecimal costUsd, Integer numTurns, Long durationMs) {
}
