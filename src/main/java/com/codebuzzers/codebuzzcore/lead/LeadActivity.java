package com.codebuzzers.codebuzzcore.lead;

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
@Table(name = "lead_activities")
public class LeadActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "lead_id", nullable = false) private Long leadId;
    @Column(name = "user_id") private Long userId;
    @Column(nullable = false, length = 32) private String type;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json") private String metadataJson;
    @Column(name = "occurred_at", nullable = false) private java.time.Instant occurredAt;

}
