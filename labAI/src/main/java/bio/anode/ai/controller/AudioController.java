package bio.anode.ai.controller;

import bio.anode.ai.controller.model.audio.AudioSpeechRequest;
import bio.anode.ai.controller.model.audio.AudioTranscriptionResponse;
import bio.anode.ai.controller.model.audio.AudioVoiceResponse;
import bio.anode.ai.service.AudioService;
import bio.anode.ai.support.NotImplementedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class AudioController {

    private final AudioService audioService;

    @GetMapping("/audio/voices")
    public Mono<AudioVoiceResponse> listVoices() {
        return Mono.just(audioService.listVoices());
    }

    @PostMapping("/audio/speech")
    public Mono<byte[]> speech(@RequestBody AudioSpeechRequest request) {
        return audioService.speech(request);
    }

    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<AudioTranscriptionResponse> transcriptions(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "model", required = false) String model) {
        return DataBufferUtilsHelper.toResource(file)
                .map(resource -> audioService.transcribe(resource, model));
    }

    @PostMapping(value = "/audio/translations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<AudioTranscriptionResponse> translations() {
        throw new NotImplementedException("Audio translations are not implemented");
    }

    /** Buffers a reactive multipart file into an in-memory Resource for the transcription model. */
    static final class DataBufferUtilsHelper {
        static Mono<org.springframework.core.io.Resource> toResource(FilePart file) {
            return org.springframework.core.io.buffer.DataBufferUtils
                    .join(file.content())
                    .map(dataBuffer -> {
                        try (var in = dataBuffer.asInputStream(true)) {
                            return (org.springframework.core.io.Resource) new NamedByteArrayResource(
                                    in.readAllBytes(), file.filename());
                        } catch (IOException e) {
                            throw new IllegalArgumentException("Could not read uploaded audio file", e);
                        }
                    });
        }
    }

    /** ByteArrayResource that reports a filename (some transcription clients require one). */
    static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] data, String filename) {
            super(data);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
