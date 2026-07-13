package bio.anode.ai.controller;

import bio.anode.ai.controller.model.images.ImageGenerationRequest;
import bio.anode.ai.controller.model.images.ImagesResponse;
import bio.anode.ai.service.ImageService;
import bio.anode.ai.support.NotImplementedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ImagesController {

    private final ImageService service;

    @PostMapping("/images/generations")
    public ImagesResponse generations(@RequestBody ImageGenerationRequest request) {
        return service.generate(request);
    }

    @PostMapping(value = "/images/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImagesResponse edits() {
        throw new NotImplementedException("Image edits are not implemented");
    }

    @PostMapping(value = "/images/variations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImagesResponse variations() {
        throw new NotImplementedException("Image variations are not implemented");
    }
}
