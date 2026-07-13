package bio.anode.ai.controller;

import bio.anode.ai.support.NotImplementedException;
import org.springframework.web.bind.annotation.*;

/** OpenAI Assistants API (Beta) — 501 stubs. */
@RestController
@RequestMapping("/api/v1/assistants")
public class AssistantsController {

    private static final String MSG = "The Assistants API is not implemented";

    @PostMapping
    public Object create(@RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @GetMapping
    public Object list() {
        throw new NotImplementedException(MSG);
    }

    @GetMapping("/{assistantId}")
    public Object retrieve(@PathVariable String assistantId) {
        throw new NotImplementedException(MSG);
    }

    @PostMapping("/{assistantId}")
    public Object modify(@PathVariable String assistantId, @RequestBody(required = false) Object request) {
        throw new NotImplementedException(MSG);
    }

    @DeleteMapping("/{assistantId}")
    public Object delete(@PathVariable String assistantId) {
        throw new NotImplementedException(MSG);
    }
}
