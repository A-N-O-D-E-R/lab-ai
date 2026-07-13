package bio.anode.ai.controller;

import bio.anode.ai.support.NotImplementedException;
import org.springframework.web.bind.annotation.*;

/** OpenAI Batches API — 501 stubs. */
@RestController
@RequestMapping("/api/v1/batches")
public class BatchesController {

    @PostMapping
    public Object create(@RequestBody(required = false) Object request) {
        throw new NotImplementedException("The Batches API is not implemented");
    }

    @GetMapping
    public Object list() {
        throw new NotImplementedException("The Batches API is not implemented");
    }

    @GetMapping("/{batchId}")
    public Object retrieve(@PathVariable String batchId) {
        throw new NotImplementedException("The Batches API is not implemented");
    }

    @PostMapping("/{batchId}/cancel")
    public Object cancel(@PathVariable String batchId) {
        throw new NotImplementedException("The Batches API is not implemented");
    }
}
