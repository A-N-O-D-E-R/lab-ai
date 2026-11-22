package bio.anode.ai.agent;

import bio.anode.ai.config.LabaiProperties.AgentProfile;
import com.anode.ai.claude.ClaudeCodeAgent;
import com.anode.ai.claude.ClaudeCodeCancelledException;
import com.anode.ai.claude.ClaudeCodeException;
import com.anode.ai.claude.ClaudeCodeOptions;
import com.anode.ai.claude.ClaudeCodeRequest;
import com.anode.ai.claude.ClaudeCodeTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * The only class that knows the Claude Code starter types. Translates a Lab AI agent invocation into a
 * hardened CLI run and maps the outcome back to Lab AI types.
 *
 * <p>Every run is headless and deny-by-default: {@code dontAsk} permission mode, only the profile's
 * built-in tools (none by default), no MCP server unless explicitly configured, and no host user/project
 * settings.
 */
@Component
@RequiredArgsConstructor
class ClaudeCodeAdapter {

    static final String PERMISSION_MODE = "dontAsk";

    private final ClaudeCodeAgent agent;

    record Invocation(String prompt, String model, String systemPrompt, Path workspace, AgentProfile profile) {
    }

    AgentOutcome run(Invocation invocation) {
        var profile = invocation.profile();
        var options = ClaudeCodeOptions.builder()
                .permissionMode(PERMISSION_MODE)
                .builtinTools(profile.builtinTools())
                .strictMcpConfig(true)
                .settingSources("")
                .timeout(profile.timeout())
                .maxTurns(profile.maxTurns())
                .maxBudgetUsd(profile.maxBudgetUsd())
                .build();
        var request = new ClaudeCodeRequest(invocation.prompt(), null, invocation.workspace(),
                invocation.model(), invocation.systemPrompt(), options);
        try {
            var result = agent.run(request);
            if (result.isError()) {
                throw new AgentException(AgentException.Reason.FAILED,
                        "Claude Code reported an error (" + result.subtype() + "): " + result.text(), null);
            }
            return new AgentOutcome(result.text(), result.sessionId(), result.costUsd(), result.numTurns(),
                    result.durationMs());
        } catch (ClaudeCodeTimeoutException e) {
            throw new AgentException(AgentException.Reason.TIMEOUT, e.getMessage(), e);
        } catch (ClaudeCodeCancelledException e) {
            throw new AgentException(AgentException.Reason.CANCELLED, e.getMessage(), e);
        } catch (ClaudeCodeException e) {
            throw new AgentException(AgentException.Reason.FAILED, e.getMessage(), e);
        }
    }
}
