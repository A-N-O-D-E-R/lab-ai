package bio.anode.ai.chat;

import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.config.LabaiProperties.Provider;
import bio.anode.ai.support.NotImplementedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Builds and resolves one {@link ChatClient} per available provider {@link ChatModel}.
 *
 * <p>Provider models are injected via {@link ObjectProvider} (resolved lazily, after the Spring AI
 * auto-configurations have registered their beans — avoiding the {@code @ConditionalOnBean}
 * ordering pitfall). A provider whose model is absent has no client; a request routed to it raises
 * {@link NotImplementedException} → HTTP 501.
 */
@Component
@Slf4j
public class ChatClientRegistry {

    private final LabaiProperties properties;
    private final Map<Provider, ChatClient> clients = new EnumMap<>(Provider.class);

    public ChatClientRegistry(
            LabaiProperties properties,
            ChatMemory chatMemory,
            ObjectProvider<BedrockProxyChatModel> bedrockChatModel,
            ObjectProvider<AnthropicChatModel> anthropicChatModel,
            ObjectProvider<OpenAiChatModel> openAiChatModel) {
        this.properties = properties;
        register(Provider.BEDROCK, bedrockChatModel.getIfAvailable(), chatMemory);
        register(Provider.ANTHROPIC, anthropicChatModel.getIfAvailable(), chatMemory);
        register(Provider.OPENAI, openAiChatModel.getIfAvailable(), chatMemory);
        log.info("Chat providers available: {}", clients.keySet());
    }

    private void register(Provider provider, ChatModel model, ChatMemory chatMemory) {
        if (model == null) {
            return;
        }
        var client = ChatClient.builder(model)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().order(1).build()
                )
                .build();
        clients.put(provider, client);
    }

    public ChatClient forModel(String modelName) {
        var provider = properties.getProvider(modelName);
        var client = provider == null ? null : clients.get(provider);
        if (client == null) {
            throw new NotImplementedException(
                    "No chat provider configured for model '" + modelName + "' (provider=" + provider + ")");
        }
        return client;
    }
}
