package bio.anode.ai.controller;

import bio.anode.ai.support.NotImplementedException;
import org.springframework.web.bind.annotation.*;

/** OpenAI Uploads API — 501 stubs. */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadsController {

    @PostMapping
    public Object create(@RequestBody(required = false) Object request) {
        throw new NotImplementedException("The Uploads API is not implemented");
    }

    @PostMapping("/{uploadId}/parts")
    public Object addPart(@PathVariable String uploadId) {
        throw new NotImplementedException("The Uploads API is not implemented");
    }

    @PostMapping("/{uploadId}/complete")
    public Object complete(@PathVariable String uploadId, @RequestBody(required = false) Object request) {
        throw new NotImplementedException("The Uploads API is not implemented");
    }

    @PostMapping("/{uploadId}/cancel")
    public Object cancel(@PathVariable String uploadId) {
        throw new NotImplementedException("The Uploads API is not implemented");
    }
}
