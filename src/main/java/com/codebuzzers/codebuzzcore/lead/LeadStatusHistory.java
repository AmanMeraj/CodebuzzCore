package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lead_status_history")
public class LeadStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "lead_id", nullable = false) private Long leadId;
    @Column(name = "from_status", length = 32) private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 32) private String toStatus;
    @Column(name = "changed_by") private Long changedBy;
    @Column(name = "changed_at", nullable = false) private java.time.Instant changedAt;

}
