package bio.anode.ai.controller.model.audio;

/** OpenAI {@code POST /audio/transcriptions} response (json format). */
public record AudioTranscriptionResponse(String text) {
}
