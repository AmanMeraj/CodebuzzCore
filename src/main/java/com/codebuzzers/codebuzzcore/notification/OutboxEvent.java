package com.codebuzzers.codebuzzcore.notification;

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
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(name = "dedupe_key", length = 160) private String dedupeKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false) private String payloadJson;
    @Column(nullable = false, length = 32) private String status;
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private java.time.Instant availableAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;

}
