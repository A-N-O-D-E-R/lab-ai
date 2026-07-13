package bio.anode.ai.controller.model.chat;

import bio.anode.ai.controller.model.chat.ChatCompletionRequest.ChatMessage;
import bio.anode.ai.controller.model.chat.ChatCompletionRequest.ContentMedia;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Configuration
public class ChatMessageDeserializer extends ValueDeserializer<List<ChatMessage>> {

    @Override
    public List<ChatMessage> deserialize(JsonParser p, DeserializationContext ctxt)
            throws JacksonException {
        JsonNode node = p.readValueAsTree();
        var messages = new ArrayList<ChatMessage>();

        for (var messageNode : node)
            messages.add(readMessage(messageNode));
        return messages;
    }

    private ChatMessage readMessage(JsonNode messageNode) {
        String userText = null;
        var medias = new ArrayList<ContentMedia>();
        var role = Optional.ofNullable(messageNode.get("role")).map(JsonNode::asString).orElse(null);
        var name = Optional.ofNullable(messageNode.get("name")).map(JsonNode::asString).orElse(null);
        var content = messageNode.get("content");

        if (content.isArray())
            for (var contentNode : content) {
                var type = contentNode.get("type").asString();

                if ("text".equals(type))
                    userText = contentNode.get("text").asString();
                else if (type != null) {
                    var data = contentNode.get(type).get("url").asString();

                    if (data != null)
                        medias.add(readMedia(data));
                }
            }
        else
            userText = content.asString();
        return new ChatMessage(role, name, userText, medias);
    }

    private ContentMedia readMedia(String encodedData) {
        var parts = encodedData.split(",", 2);
        var prefix = parts[0];
        var data = Base64.getDecoder().decode(parts[1]);
        var contentType = prefix.split("data:")[1].split(";base64")[0];

        return new ContentMedia(contentType, new ByteArrayResource(data));
    }
}
