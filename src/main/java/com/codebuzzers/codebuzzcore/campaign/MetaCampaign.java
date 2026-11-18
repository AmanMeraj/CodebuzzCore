package com.codebuzzers.codebuzzcore.campaign;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_campaigns")
public class MetaCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "ad_account_id", nullable = false) private Long adAccountId;
    @Column(name = "meta_campaign_id", nullable = false, length = 64) private String metaCampaignId;
    @Column(nullable = false) private String name;
    @Column(length = 64) private String status;
    @Column(length = 64) private String objective;
    @Column(name = "start_time") private java.time.Instant startTime;
    @Column(name = "end_time") private java.time.Instant endTime;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = java.time.Instant.now(); }

}
