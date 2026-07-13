package bio.anode.ai.controller;

import bio.anode.ai.controller.model.moderations.ModerationRequest;
import bio.anode.ai.controller.model.moderations.ModerationResponse;
import bio.anode.ai.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModerationsController {

    private final ModerationService service;

    @PostMapping("/moderations")
    public ModerationResponse moderations(@RequestBody ModerationRequest request) {
        return service.moderate(request.input(), request.model());
    }
}
