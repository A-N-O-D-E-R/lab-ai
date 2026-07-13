package bio.anode.ai.controller.model.audio;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * OpenAI {@code POST /audio/speech} request.
 */
public record AudioSpeechRequest(String model, String input, String instructions, String voice,
        @JsonAlias("response_format") String responseFormat, Double speed) {
}
