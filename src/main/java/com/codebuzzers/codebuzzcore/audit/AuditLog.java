package com.codebuzzers.codebuzzcore.audit;

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
@Table(name = "audit_logs")
public class AuditLog  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;
    @Column(name = "entity_id", length = 64)
    private String entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private String afterJson;
    @Column(nullable = false, length = 32)
    private String source;
    @Column(name = "ip_hash", length = 64)
    private String ipHash;
    @Column(name = "request_id", length = 64)
    private String requestId;
    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

}
