package bio.anode.ai.controller;

import bio.anode.ai.controller.model.embeddings.EmbeddingRequest;
import bio.anode.ai.controller.model.embeddings.EmbeddingResponse;
import bio.anode.ai.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmbeddingsController {

    private final EmbeddingService service;

    @PostMapping("/embeddings")
    public EmbeddingResponse embeddings(@RequestBody EmbeddingRequest request) {
        return service.embed(request.input(), request.model(), request.dimensions());
    }
}
