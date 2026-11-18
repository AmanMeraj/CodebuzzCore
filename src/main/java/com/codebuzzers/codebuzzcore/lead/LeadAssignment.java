package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lead_assignments")
public class LeadAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "lead_id", nullable = false) private Long leadId;
    @Column(name = "from_user_id") private Long fromUserId;
    @Column(name = "to_user_id", nullable = false) private Long toUserId;
    @Column(nullable = false, length = 64) private String reason;
    @Column(name = "attempt_no", nullable = false) private int attemptNo;
    @Column(name = "assigned_at", nullable = false) private java.time.Instant assignedAt;
    @Column(name = "response_deadline") private java.time.Instant responseDeadline;
    @Column(name = "responded_at") private java.time.Instant respondedAt;
    @Column(name = "timed_out", nullable = false) private boolean timedOut;

}
