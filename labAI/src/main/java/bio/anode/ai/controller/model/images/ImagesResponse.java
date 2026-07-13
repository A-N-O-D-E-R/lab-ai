package bio.anode.ai.controller.model.images;

import java.util.List;

public record ImagesResponse(long created, List<ImageData> data) {

    public record ImageData(String url, String b64Json, String revisedPrompt) {
    }
}
