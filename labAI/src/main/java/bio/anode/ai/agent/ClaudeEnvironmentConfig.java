package bio.anode.ai.agent;

import com.anode.ai.claude.ClaudeCodeEnvironmentProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

/**
 * Environment of every Claude Code CLI process: injects the Claude credential and removes labAI secrets
 * the CLI must not inherit.
 *
 * <p>The OAuth token is preferred. When it is set, {@code ANTHROPIC_API_KEY} is removed: the CLI gives
 * the API key precedence, and labAI's own environment carries one for the Spring AI Anthropic provider.
 */
@Configuration(proxyBeanMethods = false)
class ClaudeEnvironmentConfig {

    static final String OAUTH_TOKEN = "CLAUDE_CODE_OAUTH_TOKEN";
    static final String API_KEY = "ANTHROPIC_API_KEY";

    @Bean
    ClaudeCodeEnvironmentProvider claudeCodeEnvironmentProvider(AgentProperties properties) {
        return () -> {
            var env = new HashMap<String, String>();
            properties.scrubEnv().forEach(name -> env.put(name, null));
            if (StringUtils.isNotBlank(properties.oauthToken())) {
                env.put(OAUTH_TOKEN, properties.oauthToken());
                env.put(API_KEY, null);
            } else if (StringUtils.isNotBlank(properties.apiKey())) {
                env.put(API_KEY, properties.apiKey());
                env.put(OAUTH_TOKEN, null);
            }
            return env;
        };
    }
}
