package com.codebuzzers.codebuzzcore.webhook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id") private Long tenantId;
    @Column(nullable = false, length = 32) private String provider;
    @Column(name = "event_key", nullable = false, length = 128) private String eventKey;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false) private String payloadJson;
    @Column(name = "received_at", nullable = false) private java.time.Instant receivedAt;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "processed_at") private java.time.Instant processedAt;

}
