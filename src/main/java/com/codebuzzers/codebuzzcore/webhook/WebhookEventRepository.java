package com.codebuzzers.codebuzzcore.webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByProviderAndEventKey(String provider, String eventKey);
}
