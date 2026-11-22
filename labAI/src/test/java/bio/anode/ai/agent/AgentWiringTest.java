package bio.anode.ai.agent;

import bio.anode.ai.config.LabaiProperties;
import com.anode.ai.claude.ClaudeCodeAgent;
import com.anode.ai.claude.ClaudeCodeEnvironmentProvider;
import com.anode.ai.claude.autoconfigure.ClaudeCodeAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({LabaiProperties.class, AgentProperties.class})
    @Import({ClaudeEnvironmentConfig.class, ClaudeCodeAdapter.class, LabAgentService.class})
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClaudeCodeAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "labai.default-model.provider=bedrock",
                    "labai.models.labai-agent.provider=claude-code",
                    "labai.models.labai-agent.model-id=sonnet",
                    "labai.models.labai-agent.agent.timeout=2m",
                    "labai.models.labai-agent.agent.builtin-tools=",
                    "labai.agent.oauth-token=t");

    @Test
    void wiresAgentWithLabEnvironmentAndBindsClaudeCodeProfiles() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ClaudeCodeAgent.class).hasSingleBean(LabAgentService.class);
            assertThat(ctx.getBean(ClaudeCodeEnvironmentProvider.class).environment())
                    .containsEntry("CLAUDE_CODE_OAUTH_TOKEN", "t");

            var props = ctx.getBean(LabaiProperties.class);
            assertThat(props.getProvider("labai-agent")).isEqualTo(LabaiProperties.Provider.CLAUDE_CODE);
            assertThat(props.getAgent("labai-agent").timeout()).isEqualTo(Duration.ofMinutes(2));
            assertThat(props.getAgent("labai-agent").builtinTools()).isEmpty();
        });
    }
}
