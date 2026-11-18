package com.codebuzzers.codebuzzcore.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "support_tickets")
public class SupportTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36) private String publicId;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(nullable = false, length = 160) private String subject;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist void pre() {
        java.time.Instant now = java.time.Instant.now();
        if (publicId == null) publicId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void upd() { updatedAt = java.time.Instant.now(); }

}
