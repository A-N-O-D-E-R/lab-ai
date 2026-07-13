package bio.anode.ai.controller;

import bio.anode.ai.support.NotImplementedException;
import org.springframework.web.bind.annotation.*;

/**
 * OpenAI Threads / Messages / Runs / Run Steps APIs (Beta) — 501 stubs. Grouped under one controller
 * because they share the {@code /threads} path hierarchy.
 */
@RestController
@RequestMapping("/api/v1/threads")
public class ThreadsController {

    private static final String MSG = "The Threads/Runs API is not implemented";

    // --- Threads ---

    @PostMapping
    public Object createThread(@RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @GetMapping("/{threadId}")
    public Object retrieveThread(@PathVariable String threadId) {
        throw new NotImplementedException(MSG);
    }

    @PostMapping("/{threadId}")
    public Object modifyThread(@PathVariable String threadId, @RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @DeleteMapping("/{threadId}")
    public Object deleteThread(@PathVariable String threadId) {
        throw new NotImplementedException(MSG);
    }

    // --- Messages ---

    @PostMapping("/{threadId}/messages")
    public Object createMessage(@PathVariable String threadId, @RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @GetMapping("/{threadId}/messages")
    public Object listMessages(@PathVariable String threadId) {
        throw new NotImplementedException(MSG);
    }

    @GetMapping("/{threadId}/messages/{messageId}")
    public Object retrieveMessage(@PathVariable String threadId, @PathVariable String messageId) {
        throw new NotImplementedException(MSG);
    }

    @PostMapping("/{threadId}/messages/{messageId}")
    public Object modifyMessage(@PathVariable String threadId, @PathVariable String messageId,
                                @RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @DeleteMapping("/{threadId}/messages/{messageId}")
    public Object deleteMessage(@PathVariable String threadId, @PathVariable String messageId) {
        throw new NotImplementedException(MSG);
    }

    // --- Runs ---

    @PostMapping("/runs")
    public Object createThreadAndRun(@RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @PostMapping("/{threadId}/runs")
    public Object createRun(@PathVariable String threadId, @RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @GetMapping("/{threadId}/runs")
    public Object listRuns(@PathVariable String threadId) {
        throw new NotImplementedException(MSG);
    }

    @GetMapping("/{threadId}/runs/{runId}")
    public Object retrieveRun(@PathVariable String threadId, @PathVariable String runId) {
        throw new NotImplementedException(MSG);
    }

    @PostMapping("/{threadId}/runs/{runId}")
    public Object modifyRun(@PathVariable String threadId, @PathVariable String runId,
                            @RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @PostMapping("/{threadId}/runs/{runId}/cancel")
    public Object cancelRun(@PathVariable String threadId, @PathVariable String runId) {
        throw new NotImplementedException(MSG);
    }

    @PostMapping("/{threadId}/runs/{runId}/submit_tool_outputs")
    public Object submitToolOutputs(@PathVariable String threadId, @PathVariable String runId,
                                    @RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    // --- Run steps ---

    @GetMapping("/{threadId}/runs/{runId}/steps")
    public Object listRunSteps(@PathVariable String threadId, @PathVariable String runId) {
        throw new NotImplementedException(MSG);
    }

    @GetMapping("/{threadId}/runs/{runId}/steps/{stepId}")
    public Object retrieveRunStep(@PathVariable String threadId, @PathVariable String runId,
                                  @PathVariable String stepId) {
        throw new NotImplementedException(MSG);
    }
}
