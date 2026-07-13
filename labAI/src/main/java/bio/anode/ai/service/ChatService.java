package bio.anode.ai.service;

import bio.anode.ai.chat.ChatClientRegistry;
import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.controller.model.chat.ChatCompletionRequest;
import bio.anode.ai.controller.model.chat.ChatCompletionResponse;
import bio.anode.ai.function.AutoResponder;
import bio.anode.ai.mapper.ChatCompletionResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class ChatService {

    private final LabaiProperties labaiProperties;
    private final ChatClientRegistry chatClientRegistry;
    private final ChatMemory chatMemory;
    private final ToolCallbackResolver toolCallbackResolver;
    private final ChatCompletionResponseMapper mapper;
    private final AutoResponder autoResponder;
    private final bio.anode.ai.chat.QaAdvisorFactory qaAdvisorFactory;

    private ChatClient.ChatClientRequestSpec getClient(
            String modelName,
            String sessionId,
            Message userMessage) {

        var options = ChatOptions.builder()
                .model(labaiProperties.getModelId(modelName))
                .build();

        var prompt = new Prompt(userMessage, options);
        var spec = chatClientRegistry.forModel(modelName)
                .prompt(prompt)
                .tools(getTools(modelName))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

        // Attach RAG retrieval only for models bound to a knowledge base.
        var knowledgeBase = labaiProperties.getKnowledgeBase(modelName);
        if (knowledgeBase != null && !knowledgeBase.isBlank()) {
            spec = spec.advisors(qaAdvisorFactory.forStore(knowledgeBase, labaiProperties.getPromptRag(modelName)));
        }
        return spec;
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request, String sessionId) {
        var modelName = request.model();
        var userMessage = getUserMessage(request);
        return autoResponder.detect(userMessage.getText())
                .map(autoReply -> buildAutoReply(modelName, autoReply))
                .orElseGet(() -> mapper.toWeb(modelName,
                        getClient(modelName, sessionId, userMessage).call().chatResponse(), false));
    }

    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request, String sessionId) {
        var modelName = request.model();
        var userMessage = getUserMessage(request);
        return autoResponder.detect(userMessage.getText())
                .map(autoReply -> Flux.just(buildAutoReply(modelName, autoReply)))
                .orElseGet(() -> getClient(modelName, sessionId, userMessage).stream().chatResponse()
                        .map(c -> mapper.toWeb(modelName, c, true)));
    }

    private ChatCompletionResponse buildAutoReply(String application, String reply) {
        return new ChatCompletionResponse(
                null,
                application,
                List.of(new ChatCompletionResponse.Choice(
                        "stop",
                        0,
                        new ChatCompletionResponse.Message("assistant", reply, null)
                )),
                null,
                true
        );
    }

    private Message getUserMessage(ChatCompletionRequest request) {
        Message message = mapper.toMessage(request.messages().getLast());
        Assert.isInstanceOf(UserMessage.class, message);
        return message;
    }

    private List<ToolCallback> getTools(String application) {
        var toolNames = new HashSet<String>();
        toolNames.addAll(labaiProperties.getTools(application));
        toolNames.addAll(labaiProperties.globalTools());
        if (toolNames.isEmpty()) {
            return List.of();
        }

        return toolNames.stream()
                .map(toolCallbackResolver::resolve)
                .filter(Objects::nonNull)
                .toList();
    }

    public void resetConversationMemory(String sessionId, List<ChatCompletionRequest.ChatMessage> messages) {
        chatMemory.clear(sessionId);

        messages.subList(0, messages.size() - 1)
                .stream()
                .map(mapper::toMessage)
                .forEach(message -> chatMemory.add(sessionId, message));
    }
}
