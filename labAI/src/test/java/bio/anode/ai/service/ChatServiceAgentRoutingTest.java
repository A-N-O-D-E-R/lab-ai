package bio.anode.ai.service;

import bio.anode.ai.agent.AgentOutcome;
import bio.anode.ai.agent.LabAgentService;
import bio.anode.ai.chat.ChatClientRegistry;
import bio.anode.ai.chat.QaAdvisorFactory;
import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.config.LabaiProperties.ModelProperty;
import bio.anode.ai.config.LabaiProperties.Provider;
import bio.anode.ai.controller.model.chat.ChatCompletionRequest;
import bio.anode.ai.controller.model.chat.ChatCompletionRequest.ChatMessage;
import bio.anode.ai.function.AutoResponder;
import bio.anode.ai.mapper.ChatCompletionResponseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatServiceAgentRoutingTest {

    private final ChatClientRegistry registry = mock(ChatClientRegistry.class);
    private final ChatCompletionResponseMapper mapper = mock(ChatCompletionResponseMapper.class);
    private final AutoResponder autoResponder = mock(AutoResponder.class);
    private final LabAgentService agent = mock(LabAgentService.class);
    private final ChatService service;

    ChatServiceAgentRoutingTest() {
        var agentModel = new ModelProperty(null, null, Provider.CLAUDE_CODE, "sonnet", null, false, false,
                null, null, null, null, null, null);
        var properties = new LabaiProperties(agentModel, Map.of("labai-agent", agentModel), null, null, null);
        service = new ChatService(properties, registry, mock(ChatMemory.class), mock(ToolCallbackResolver.class),
                mapper, autoResponder, mock(QaAdvisorFactory.class), agent);
        when(autoResponder.detect(any())).thenReturn(Optional.empty());
        when(mapper.toMessage(any(ChatMessage.class))).thenAnswer(i ->
                new UserMessage(i.getArgument(0, ChatMessage.class).content()));
        when(agent.run(anyString(), anyString())).thenReturn(new AgentOutcome("answer", "s", null, 1, 10L));
    }

    private static ChatCompletionRequest request(boolean stream, ChatMessage... messages) {
        return new ChatCompletionRequest(null, null, List.of(messages), "labai-agent", stream, null);
    }

    @Test
    void claudeCodeModelGoesToAgentNotToSpringAiChatClient() {
        var response = service.chatCompletion(request(false, new ChatMessage("user", null, "hi", null)), "conv");

        assertThat(response.choices().getFirst().message().content()).isEqualTo("answer");
        assertThat(response.object()).isEqualTo("chat.completion");
        verify(agent).run("labai-agent", "hi");
        verifyNoInteractions(registry);
    }

    @Test
    void streamingEmitsOneChunk() {
        StepVerifier.create(service.chatCompletionStream(request(true, new ChatMessage("user", null, "hi", null)), "c"))
                .assertNext(r -> {
                    assertThat(r.object()).isEqualTo("chat.completion.chunk");
                    assertThat(r.choices().getFirst().delta().content()).isEqualTo("answer");
                })
                .verifyComplete();
        verifyNoInteractions(registry);
    }

    @Test
    void historyIsFlattenedIntoThePrompt() {
        var prompt = ChatService.toAgentPrompt(List.of(
                new ChatMessage("user", null, "Q1", null),
                new ChatMessage("assistant", null, "A1", null),
                new ChatMessage("user", null, "Q2", null)));
        assertThat(prompt).isEqualTo("User:\nQ1\n\nAssistant:\nA1\n\nUser:\nQ2");
    }
}
