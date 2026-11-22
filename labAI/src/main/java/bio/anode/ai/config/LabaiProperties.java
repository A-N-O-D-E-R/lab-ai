package bio.anode.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

/**
 * Configuration for the labAI OpenAI-compatible gateway, bound under the {@code labai.*} prefix.
 *
 * <p>Each entry in {@link #models()} maps an OpenAI-facing model id to a {@link Provider} and the
 * provider-native model id used when dispatching the request through Spring AI.
 */
@ConfigurationProperties(prefix = "labai")
public record LabaiProperties(ModelProperty defaultModel,
                              Map<String, ModelProperty> models,
                              Set<String> globalTools,
                              List<AutoResponse> autoResponses,
                              Map<String, String> apikeys) {

    public LabaiProperties {
        models = models == null ? Map.of() : models;
        globalTools = globalTools == null ? Set.of() : globalTools;
        autoResponses = autoResponses == null ? List.of() : autoResponses;
        apikeys = apikeys == null ? Map.of() : apikeys;
    }

    /** Supported upstream LLM providers. {@code CLAUDE_CODE} models run as autonomous Claude Code agents. */
    public enum Provider {
        BEDROCK, ANTHROPIC, OPENAI, CLAUDE_CODE
    }

    /** Kind of model, used to route to the correct Spring AI model abstraction. */
    public enum ModelType {
        CHAT, EMBEDDING, IMAGE, TRANSCRIPTION, MODERATION
    }

    public ModelProperty model(String appName) {
        return models().getOrDefault(appName, defaultModel());
    }

    /** Resolve the provider-native model id for a given OpenAI-facing model name. */
    public String getModelId(String appName) {
        var model = model(appName);
        return Optional.ofNullable(model.modelId())
                .orElseGet(() -> defaultModel().modelId());
    }

    /** Resolve the provider for a given OpenAI-facing model name. */
    public Provider getProvider(String appName) {
        var model = model(appName);
        return Optional.ofNullable(model.provider())
                .orElseGet(() -> defaultModel().provider());
    }

    public String getPrompt(String appName) {
        var model = model(appName);
        var prompt = Optional.ofNullable(model.prompt()).orElseGet(() -> defaultModel().prompt());
        return prompt == null ? null : prompt.replace("@application@", appName);
    }

    /** RAG prompt template for the model (with {@code @application@} substituted); null if none. */
    public String getPromptRag(String appName) {
        var model = model(appName);
        var promptRag = Optional.ofNullable(model.promptRag()).orElseGet(() -> defaultModel().promptRag());
        return promptRag == null ? null : promptRag.replace("@application@", appName);
    }

    /** Knowledge-base (vector store) id bound to this model, or null when the model has no RAG. */
    public String getKnowledgeBase(String appName) {
        return model(appName).knowledgeBase();
    }

    /** Agent settings of a {@code claude-code} model; never null (defaults when the model has no {@code agent} block). */
    public AgentProfile getAgent(String appName) {
        return Optional.ofNullable(model(appName).agent()).orElseGet(AgentProfile::defaults);
    }

    public Set<String> getTools(String appName) {
        var model = model(appName);
        return Optional.ofNullable(model.tools()).orElseGet(Collections::emptySet);
    }

    public record ModelProperty(String name,
                                String description,
                                Provider provider,
                                String modelId,
                                ModelType type,
                                boolean onlyForAdmins,
                                boolean onlyForSuperAdmins,
                                String greeting,
                                String prompt,
                                String promptRag,
                                String knowledgeBase,
                                Set<String> tools,
                                AgentProfile agent) {

        public ModelType type() {
            return type == null ? ModelType.CHAT : type;
        }
    }

    /**
     * Claude Code agent settings of a {@code claude-code} model.
     *
     * @param builtinTools Claude Code built-in tools; empty (the default) disables all of them
     * @param timeout      maximum duration of one run
     * @param maxTurns     maximum agent turns per run
     * @param maxBudgetUsd maximum API spend per run
     */
    public record AgentProfile(List<String> builtinTools, Duration timeout, Integer maxTurns, BigDecimal maxBudgetUsd) {

        public AgentProfile {
            builtinTools = builtinTools == null ? List.of() : List.copyOf(builtinTools);
            timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
            maxTurns = maxTurns == null ? 20 : maxTurns;
        }

        public static AgentProfile defaults() {
            return new AgentProfile(null, null, null, null);
        }
    }

    public record AutoResponse(String name, boolean enabled, Set<String> triggers, String response) {
    }
}
