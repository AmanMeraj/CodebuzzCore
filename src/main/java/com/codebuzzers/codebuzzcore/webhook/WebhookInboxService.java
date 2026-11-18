package com.codebuzzers.codebuzzcore.webhook;

import com.codebuzzers.codebuzzcore.common.AppProperties;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.Hashes;
import com.codebuzzers.codebuzzcore.common.Jsons;
import com.codebuzzers.codebuzzcore.job.JobQueue;
import com.codebuzzers.codebuzzcore.meta.WebhookSignatureVerifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Service
public class WebhookInboxService {

    private final AppProperties properties;
    private final WebhookSignatureVerifier verifier;
    private final WebhookEventRepository events;
    private final JobQueue jobs;
    private final ObjectMapper objectMapper;

    public WebhookInboxService(AppProperties properties, WebhookSignatureVerifier verifier,
                               WebhookEventRepository events, JobQueue jobs, ObjectMapper objectMapper) {
        this.properties = properties;
        this.verifier = verifier;
        this.events = events;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    public boolean verifySubscription(String mode, String token) {
        String expected = properties.meta().webhookVerifyToken();
        return "subscribe".equals(mode)
                && expected != null
                && !expected.isBlank()
                && token != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    public boolean signatureOk(String rawPayload, String signature) {
        return verifier.isValid(rawPayload, signature, properties.meta().appSecret());
    }

    @Transactional
    public void accept(String rawPayload) {
        String eventKey = eventKey(rawPayload);
        if (events.findByProviderAndEventKey("meta", eventKey).isPresent()) {
            return;
        }
        boolean validJson = true;
        try {
            objectMapper.readTree(rawPayload);
        } catch (Exception ex) {
            validJson = false;
        }
        WebhookEvent event = new WebhookEvent();
        event.setProvider("meta");
        event.setEventKey(eventKey);
        event.setPayloadHash(Hashes.sha256(rawPayload));
        event.setPayloadJson(validJson ? rawPayload : objectMapper.writeValueAsString(java.util.Map.of("unparsed", true)));
        event.setReceivedAt(Instant.now());
        event.setStatus(validJson ? Codes.WebhookStatus.RECEIVED : Codes.WebhookStatus.DEAD);
        event.setErrorCode(validJson ? null : "malformed_payload");
        try {
            events.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            return;
        }
        if (validJson) {
            jobs.enqueue(null, Codes.JobType.INGEST_LEAD, "ingest:" + event.getId(),
                    "{\"webhookEventId\":" + event.getId() + "}");
        }
    }

    private String eventKey(String rawPayload) {
        try {
            JsonNode payload = objectMapper.readTree(rawPayload);
            for (JsonNode entry : payload.path("entry")) {
                String pageId = Jsons.text(entry.path("id"));
                for (JsonNode change : entry.path("changes")) {
                    String leadgenId = Jsons.text(change.path("value").path("leadgen_id"));
                    if (leadgenId != null) {
                        return "leadgen:" + (pageId == null ? "" : pageId) + ":" + leadgenId;
                    }
                }
            }
        } catch (Exception ignored) {
            // A body that is not JSON still gets a stable fingerprint and is dead-lettered by the worker.
        }
        return "sha256:" + Hashes.sha256(rawPayload);
    }
}
