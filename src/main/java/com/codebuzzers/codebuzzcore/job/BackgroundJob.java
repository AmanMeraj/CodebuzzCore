package com.codebuzzers.codebuzzcore.job;

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
@Table(name = "background_jobs")
public class BackgroundJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "job_type", nullable = false, length = 64) private String jobType;
    @Column(name = "job_key", length = 160) private String jobKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false) private String payloadJson;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "available_at", nullable = false) private java.time.Instant availableAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "locked_at") private java.time.Instant lockedAt;
    @Column(name = "locked_by", length = 64) private String lockedBy;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist void pre() {
        java.time.Instant now = java.time.Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void upd() { updatedAt = java.time.Instant.now(); }

}
