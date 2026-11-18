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
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(nullable = false, length = 64) private String operation;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json") private String responseJson;
    @Column(name = "expires_at", nullable = false) private java.time.Instant expiresAt;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;

}
