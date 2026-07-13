package bio.anode.ai.mapper;

import bio.anode.ai.controller.model.chat.ChatCompletionRequest;
import bio.anode.ai.controller.model.chat.ChatCompletionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChatCompletionResponseMapper {

    // "Classic" choice: the content is carried by `message`
    @Mapping(target = "index", constant = "0")
    @Mapping(target = "finishReason", source = "metadata.finishReason")
    @Mapping(target = "message", source = "output")
    @Mapping(target = "delta", ignore = true)
    ChatCompletionResponse.Choice toChoice(Generation r);

    // "Stream" choice: the content is carried by `delta`
    @Mapping(target = "index", constant = "0")
    @Mapping(target = "finishReason", source = "metadata.finishReason")
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "delta", source = "output")
    ChatCompletionResponse.Choice toStreamChoice(Generation r);


    @Mapping(target = "role", source = "messageType.value")
    @Mapping(target = "content", source = "text")
    @Mapping(target = "toolCalls", source = "toolCalls")
    ChatCompletionResponse.Message toMessage(AssistantMessage output);

    @Mapping(target = "type", constant = "function")
    @Mapping(target = "function", source = "r")
    ChatCompletionResponse.ToolCall toToolCall(ChatCompletionResponse.ToolCallFunction r);

    @Mapping(target = "name", source = "function.name")
    @Mapping(target = "arguments", source = "function.arguments")
    ChatCompletionResponse.ToolCallFunction toToolCallFunction(ChatCompletionResponse.ToolCall r);

    List<Media> toMedias(List<ChatCompletionRequest.ContentMedia> source);

    ChatCompletionResponse.Usage toUsage(DefaultUsage usage);

    default Media toMedia(ChatCompletionRequest.ContentMedia source) {
        if (source == null)
            return null;
        return Media.builder()
                .mimeType(MimeType.valueOf(source.contentType()))
                .data(source.content())
                .build();
    }

    default ChatCompletionResponse toWeb(String application, ChatResponse response, boolean isStream) {
        if (response == null)
            return null;
        var choices = response.getResults()
                .stream()
                .map(isStream ? this::toStreamChoice : this::toChoice)
                .toList();

        var usage = response.getMetadata().getUsage() instanceof DefaultUsage defaultUsage
                ? toUsage(defaultUsage)
                : null;

        return new ChatCompletionResponse(
                response.getMetadata().getId(),
                application,
                choices,
                usage,
                isStream
        );
    }

    default Message toMessage(ChatCompletionRequest.ChatMessage message) {
        if (message == null)
            return null;
        return switch (message.role()) {
            case "user" -> UserMessage.builder()
                    .text(message.content())
                    .media(toMedias(message.medias()))
                    .build();
            case "assistant" -> new AssistantMessage(message.content());
            case "system" -> new SystemMessage(message.content());
            default -> {
                LoggerFactory.getLogger(ChatCompletionResponseMapper.class)
                        .warn("Unhandled role received: {}", message.role());
                yield new AssistantMessage(message.content());
            }
        };
    }
}
