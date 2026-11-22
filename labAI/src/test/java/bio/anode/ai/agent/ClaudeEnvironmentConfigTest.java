package bio.anode.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeEnvironmentConfigTest {

    private final ClaudeEnvironmentConfig config = new ClaudeEnvironmentConfig();

    @Test
    void oauthTokenWinsAndInheritedApiKeyIsRemoved() {
        var env = config.claudeCodeEnvironmentProvider(
                new AgentProperties(null, "oauth-token", "api-key", Set.of("AWS_SECRET_ACCESS_KEY"))).environment();

        assertThat(env).containsEntry("CLAUDE_CODE_OAUTH_TOKEN", "oauth-token");
        // null = removed from the CLI process environment
        assertThat(env).containsEntry("ANTHROPIC_API_KEY", null);
        assertThat(env).containsEntry("AWS_SECRET_ACCESS_KEY", null);
    }

    @Test
    void apiKeyUsedWhenNoOauthToken() {
        var env = config.claudeCodeEnvironmentProvider(new AgentProperties(null, "", "api-key", null)).environment();

        assertThat(env).containsEntry("ANTHROPIC_API_KEY", "api-key");
        assertThat(env).containsEntry("CLAUDE_CODE_OAUTH_TOKEN", null);
    }
}
