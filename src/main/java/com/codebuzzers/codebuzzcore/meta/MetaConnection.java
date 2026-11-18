package com.codebuzzers.codebuzzcore.meta;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_connections")
public class MetaConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "meta_user_id", length = 64)
    private String metaUserId;
    @Column(name = "meta_user_name")
    private String metaUserName;
    @Column(name = "encrypted_access_token", columnDefinition = "text")
    private String encryptedAccessToken;
    @Column(name = "token_expires_at")
    private java.time.Instant tokenExpiresAt;
    @Column(name = "granted_scopes", length = 500)
    private String grantedScopes;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "health_message")
    private String healthMessage;
    @Column(name = "connected_by_user_id")
    private Long connectedByUserId;
    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;
    @Column(name = "disconnected_at")
    private java.time.Instant disconnectedAt;

    @PrePersist
    void prePersist() {
        java.time.Instant now = java.time.Instant.now();
        if (publicId == null) publicId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = java.time.Instant.now(); }

}
