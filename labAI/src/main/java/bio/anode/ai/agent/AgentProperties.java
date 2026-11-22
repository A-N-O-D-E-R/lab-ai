package bio.anode.ai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Set;

/**
 * Claude Code agent runtime settings, bound under {@code labai.agent.*}.
 *
 * @param workspaceRoot root directory of agent working directories; runs never leave it
 * @param oauthToken    Claude Code OAuth token ({@code claude setup-token}); preferred credential
 * @param apiKey        Anthropic Console API key, used only when no OAuth token is set
 * @param scrubEnv      environment variables removed from the CLI process (labAI secrets it must not inherit)
 */
@ConfigurationProperties(prefix = "labai.agent")
public record AgentProperties(Path workspaceRoot, String oauthToken, String apiKey, Set<String> scrubEnv) {

    public AgentProperties {
        workspaceRoot = (workspaceRoot == null ? Path.of("workspaces") : workspaceRoot).toAbsolutePath().normalize();
        scrubEnv = scrubEnv == null ? Set.of() : scrubEnv;
    }
}
