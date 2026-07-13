package bio.anode.ai.controller;

import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.controller.model.common.DeletedObject;
import bio.anode.ai.controller.model.models.Model;
import bio.anode.ai.controller.model.models.Models;
import bio.anode.ai.support.NotImplementedException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModelController {

    private final LabaiProperties labaiProperties;

    @GetMapping("/models")
    public Mono<Models> listModels() {
        var models = labaiProperties.models().keySet().stream()
                .map(Model::new)
                .toList();
        return Mono.just(new Models(models));
    }

    @GetMapping("/models/{model}")
    public Mono<Model> getModel(@PathVariable String model) {
        return Mono.just(new Model(model));
    }

    @DeleteMapping("/models/{model}")
    public DeletedObject deleteModel(@PathVariable String model) {
        // Fine-tuned model deletion has no backing in this gateway.
        throw new NotImplementedException("Deleting models is not supported");
    }
}
