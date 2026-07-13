package bio.anode.ai.service;

import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.controller.model.images.ImageGenerationRequest;
import bio.anode.ai.controller.model.images.ImagesResponse;
import bio.anode.ai.support.NotImplementedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs {@code POST /api/v1/images/generations}. Uses whichever {@link ImageModel} is configured
 * (OpenAI); returns 501 when none is available.
 */
@Service
@Slf4j
public class ImageService {

    private final ObjectProvider<ImageModel> imageModel;
    private final LabaiProperties properties;

    public ImageService(ObjectProvider<ImageModel> imageModel, LabaiProperties properties) {
        this.imageModel = imageModel;
        this.properties = properties;
    }

    public ImagesResponse generate(ImageGenerationRequest request) {
        var model = imageModel.getIfAvailable();
        if (model == null) {
            throw new NotImplementedException("No image provider is configured");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("'prompt' is required");
        }

        var optionsBuilder = ImageOptionsBuilder.builder()
                .model(properties.getModelId(request.model()));
        if (request.n() != null) optionsBuilder.n(request.n());
        if (request.responseFormat() != null) optionsBuilder.responseFormat(request.responseFormat());
        applySize(optionsBuilder, request.size());
        if (request.style() != null) optionsBuilder.style(request.style());

        var response = model.call(new ImagePrompt(request.prompt(), optionsBuilder.build()));

        var data = response.getResults().stream()
                .map(g -> new ImagesResponse.ImageData(
                        g.getOutput().getUrl(),
                        g.getOutput().getB64Json(),
                        null))
                .toList();

        return new ImagesResponse(System.currentTimeMillis() / 1_000L, List.copyOf(data));
    }

    private void applySize(ImageOptionsBuilder builder, String size) {
        if (size == null || !size.contains("x")) return;
        var parts = size.toLowerCase().split("x", 2);
        try {
            builder.width(Integer.parseInt(parts[0].trim()));
            builder.height(Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            log.debug("Ignoring unparseable image size: {}", size);
        }
    }
}
