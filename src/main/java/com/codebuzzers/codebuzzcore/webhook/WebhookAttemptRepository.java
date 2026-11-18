package com.codebuzzers.codebuzzcore.webhook;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WebhookAttemptRepository extends JpaRepository<WebhookAttempt, Long> {
    int countByWebhookEventId(Long webhookEventId);
}
