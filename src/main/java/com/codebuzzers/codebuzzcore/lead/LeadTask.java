package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lead_tasks")
public class LeadTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36) private String publicId;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "lead_id", nullable = false) private Long leadId;
    @Column(name = "assigned_user_id", nullable = false) private Long assignedUserId;
    @Column(nullable = false, length = 32) private String type;
    @Column(name = "due_at", nullable = false) private java.time.Instant dueAt;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "completed_at") private java.time.Instant completedAt;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;
    @PrePersist void pre() {
        if (publicId == null) publicId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = java.time.Instant.now();
    }

}
