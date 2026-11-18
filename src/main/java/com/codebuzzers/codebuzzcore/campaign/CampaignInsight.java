package com.codebuzzers.codebuzzcore.campaign;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campaign_insights")
public class CampaignInsight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "object_type", nullable = false, length = 32) private String objectType;
    @Column(name = "meta_object_id", nullable = false, length = 64) private String metaObjectId;
    @Column(name = "insight_date", nullable = false) private java.time.LocalDate insightDate;
    @Column(nullable = false, precision = 18, scale = 4) private java.math.BigDecimal spend;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(nullable = false) private long impressions;
    @Column(nullable = false) private long clicks;
    @Column(nullable = false) private long leads;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = java.time.Instant.now(); }

}
