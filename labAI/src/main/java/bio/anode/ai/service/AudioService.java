package bio.anode.ai.service;

import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.controller.model.audio.AudioSpeechRequest;
import bio.anode.ai.controller.model.audio.AudioTranscriptionResponse;
import bio.anode.ai.controller.model.audio.AudioVoiceResponse;
import bio.anode.ai.support.NotImplementedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.Voice;

import java.util.Optional;

/**
 * Audio endpoints: speech synthesis via AWS Polly (optional) and transcription via the configured
 * Spring AI {@link TranscriptionModel} (OpenAI Whisper). Each degrades to 501 when its backend is
 * absent.
 */
@Service
@Slf4j
public class AudioService {

    private final ObjectProvider<PollyClient> pollyClient;
    private final ObjectProvider<Voice> voice;
    private final ObjectProvider<TranscriptionModel> transcriptionModel;
    private final LabaiProperties properties;

    public AudioService(ObjectProvider<PollyClient> pollyClient,
                        ObjectProvider<Voice> voice,
                        ObjectProvider<TranscriptionModel> transcriptionModel,
                        LabaiProperties properties) {
        this.pollyClient = pollyClient;
        this.voice = voice;
        this.transcriptionModel = transcriptionModel;
        this.properties = properties;
    }

    public AudioVoiceResponse listVoices() {
        var v = voice.getIfAvailable();
        if (v == null) {
            throw new NotImplementedException("Text-to-speech is not configured");
        }
        return new AudioVoiceResponse(v.idAsString(), v.name());
    }

    public Mono<byte[]> speech(AudioSpeechRequest request) {
        var polly = pollyClient.getIfAvailable();
        var v = voice.getIfAvailable();
        if (polly == null || v == null) {
            throw new NotImplementedException("Text-to-speech is not configured");
        }
        return Mono.fromCallable(() -> {
            var format = Optional.ofNullable(request.responseFormat()).orElse("mp3");
            var req = SynthesizeSpeechRequest.builder()
                    .text(request.input())
                    .engine(Engine.NEURAL)
                    .languageCode(v.languageCode())
                    .voiceId(v.id())
                    .outputFormat(format)
                    .build();

            log.debug("Synthesize: request={}", request);
            return polly.synthesizeSpeechAsBytes(req).asByteArray();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public AudioTranscriptionResponse transcribe(Resource audio, String model) {
        var tm = transcriptionModel.getIfAvailable();
        if (tm == null) {
            throw new NotImplementedException("Audio transcription is not configured");
        }
        var response = tm.call(new AudioTranscriptionPrompt(audio));
        return new AudioTranscriptionResponse(response.getResult().getOutput());
    }
}
