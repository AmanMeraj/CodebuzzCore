package com.codebuzzers.codebuzzcore.webhook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "webhook_attempts")
public class WebhookAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_event_id", nullable = false) private Long webhookEventId;
    @Column(name = "attempt_no", nullable = false) private int attemptNo;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(name = "attempted_at", nullable = false) private java.time.Instant attemptedAt;

}
