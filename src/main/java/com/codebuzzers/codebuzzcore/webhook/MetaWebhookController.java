package com.codebuzzers.codebuzzcore.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/meta")
public class MetaWebhookController {

    private final WebhookInboxService inbox;

    public MetaWebhookController(WebhookInboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        if (inbox.verifySubscription(mode, token) && challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!inbox.signatureOk(rawPayload, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        inbox.accept(rawPayload);
        return ResponseEntity.ok().build();
    }
}
