package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assignment_state")
public class AssignmentState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "team_id", nullable = false) private Long teamId;
    @Column(name = "last_assigned_user_id") private Long lastAssignedUserId;
    @Version
    @Column(nullable = false) private long version;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = java.time.Instant.now(); }

}
