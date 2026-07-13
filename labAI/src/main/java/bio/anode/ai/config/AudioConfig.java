package bio.anode.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.DescribeVoicesRequest;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.LanguageCode;
import software.amazon.awssdk.services.polly.model.Voice;

/**
 * AWS Polly text-to-speech wiring, backing {@code POST /api/v1/audio/speech}.
 *
 * <p>Gated on {@code labai.audio.polly.enabled} (default true) so the gateway can run without AWS
 * credentials by disabling TTS.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "labai.audio.polly.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AudioConfig {

    @Bean
    PollyClient pollyClient(@Value("${labai.audio.polly.region:${AWS_REGION:eu-west-3}}") String region) {
        return PollyClient.builder().region(Region.of(region)).build();
    }

    @Bean
    Voice voice(PollyClient pollyClient,
                @Value("${labai.audio.polly.language-code:en-US}") String languageCode) {
        var request = DescribeVoicesRequest.builder()
                .engine(Engine.NEURAL)
                .languageCode(LanguageCode.fromValue(languageCode))
                .build();
        var response = pollyClient.describeVoices(request);

        log.debug("Voices: {}", response);
        return response.voices().getFirst();
    }
}
