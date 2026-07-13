package bio.anode.ai.controller;

import bio.anode.ai.controller.model.chat.ChatCompletionRequest;
import bio.anode.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ChatCompletionController {

    private final ChatService service;

    @PostMapping("/chat/completions")
    public ResponseEntity<?> completion(@RequestBody ChatCompletionRequest request) {
        log.trace("Request: {}", request);
        Assert.notEmpty(request.messages(), "At least one message is required");
        var sessionId = UUID.randomUUID().toString();
        service.resetConversationMemory(sessionId, request.messages()); // ensure no collision
        if (request.isStream()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(service.chatCompletionStream(request, sessionId));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.chatCompletion(request, sessionId));
    }
}
