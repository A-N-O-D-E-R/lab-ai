package bio.anode.ai.controller;

import bio.anode.ai.support.NotImplementedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Legacy OpenAI text completions endpoint — 501 stub (use /chat/completions instead). */
@RestController
@RequestMapping("/api/v1")
public class LegacyCompletionController {

    @PostMapping("/completions")
    public Object completions(@RequestBody(required = false) Object request) {
        throw new NotImplementedException("The legacy Completions API is not implemented; use /chat/completions");
    }
}
