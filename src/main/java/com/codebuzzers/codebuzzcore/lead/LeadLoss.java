package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lead_losses")
public class LeadLoss {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "lead_id", nullable = false) private Long leadId;
    @Column(name = "reason_id", nullable = false) private Long reasonId;
    @Column(length = 500) private String notes;
    @Column(name = "lost_at", nullable = false) private java.time.Instant lostAt;

}
