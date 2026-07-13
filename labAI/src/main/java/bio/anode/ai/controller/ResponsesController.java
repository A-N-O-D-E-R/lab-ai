package bio.anode.ai.controller;

import bio.anode.ai.controller.model.responses.ResponsesRequest;
import bio.anode.ai.support.NotImplementedException;
import org.springframework.web.bind.annotation.*;

/**
 * OpenAI Responses API. Spring AI 2.0 has no Responses abstraction, so every route is a 501 stub
 * with correct routing and request DTOs.
 */
@RestController
@RequestMapping("/api/v1/responses")
public class ResponsesController {

    @PostMapping
    public Object create(@RequestBody(required = false) ResponsesRequest request) {
        throw new NotImplementedException("The Responses API is not implemented");
    }

    @GetMapping("/{responseId}")
    public Object get(@PathVariable String responseId) {
        throw new NotImplementedException("The Responses API is not implemented");
    }

    @DeleteMapping("/{responseId}")
    public Object delete(@PathVariable String responseId) {
        throw new NotImplementedException("The Responses API is not implemented");
    }

    @PostMapping("/{responseId}/cancel")
    public Object cancel(@PathVariable String responseId) {
        throw new NotImplementedException("The Responses API is not implemented");
    }

    @GetMapping("/{responseId}/input_items")
    public Object inputItems(@PathVariable String responseId) {
        throw new NotImplementedException("The Responses API is not implemented");
    }
}
