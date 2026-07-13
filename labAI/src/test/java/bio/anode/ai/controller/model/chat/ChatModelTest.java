package bio.anode.ai.controller.model.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@JsonTest
public class ChatModelTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void testJson() {
        var json = """
{
    "stream": true,
    "model": "labai-bedrock-sonnet",
    "messages": [
        {
            "role": "user",
            "content": "Question 1."
        },
        {
            "role": "assistant",
            "content": "Answer 1."
        },
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "What does this image represent?"
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": "data:image/png;base64,YWJj"
                    }
                }
            ]
        }
    ]
}
                """;

        var r = jsonMapper.readValue(json, ChatCompletionRequest.class);

        assertNotNull(r);
        assertNotNull(r.messages());
        assertEquals(3, r.messages().size());
        //
        assertEquals("Question 1.", r.messages().get(0).content());
        assertNotNull(r.messages().get(0).medias());
        assertEquals(0, r.messages().get(0).medias().size());
        //
        assertEquals("Answer 1.", r.messages().get(1).content());
        assertNotNull(r.messages().get(1).medias());
        assertEquals(0, r.messages().get(1).medias().size());
        //
        assertEquals("What does this image represent?", r.messages().get(2).content());
        assertNotNull(r.messages().get(2).medias());
        assertEquals(1, r.messages().get(2).medias().size());
        assertEquals("image/png", r.messages().get(2).medias().get(0).contentType());
        assertNotNull(r.messages().get(2).medias().get(0).content());
    }
}
