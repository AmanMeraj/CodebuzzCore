package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lead_notes")
public class LeadNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "lead_id", nullable = false) private Long leadId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;

}
