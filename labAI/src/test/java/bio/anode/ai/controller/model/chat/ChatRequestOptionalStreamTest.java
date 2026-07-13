package bio.anode.ai.controller.model.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JsonTest
public class ChatRequestOptionalStreamTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void assistantOnlyStringContent() {
        var json = """
                {"model":"m","messages":[{"role":"assistant","content":"a"}]}
                """;
        var r = jsonMapper.readValue(json, ChatCompletionRequest.class);
        assertEquals(1, r.messages().size());
    }

    @Test
    void twoUserStringMessages() {
        var json = """
                {"model":"m","messages":[{"role":"user","content":"a"},{"role":"user","content":"b"}]}
                """;
        var r = jsonMapper.readValue(json, ChatCompletionRequest.class);
        assertEquals(2, r.messages().size());
    }
}
