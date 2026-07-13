package bio.anode.ai.controller;

import bio.anode.ai.support.NotImplementedException;
import org.springframework.web.bind.annotation.*;

/** OpenAI Fine-tuning API — 501 stubs (no training backend). */
@RestController
@RequestMapping("/api/v1/fine_tuning/jobs")
public class FineTuningController {

    @PostMapping
    public Object create(@RequestBody(required = false) Object request) {
        throw new NotImplementedException("The Fine-tuning API is not implemented");
    }

    @GetMapping
    public Object list() {
        throw new NotImplementedException("The Fine-tuning API is not implemented");
    }

    @GetMapping("/{jobId}")
    public Object retrieve(@PathVariable String jobId) {
        throw new NotImplementedException("The Fine-tuning API is not implemented");
    }

    @PostMapping("/{jobId}/cancel")
    public Object cancel(@PathVariable String jobId) {
        throw new NotImplementedException("The Fine-tuning API is not implemented");
    }

    @GetMapping("/{jobId}/events")
    public Object events(@PathVariable String jobId) {
        throw new NotImplementedException("The Fine-tuning API is not implemented");
    }

    @GetMapping("/{jobId}/checkpoints")
    public Object checkpoints(@PathVariable String jobId) {
        throw new NotImplementedException("The Fine-tuning API is not implemented");
    }
}
