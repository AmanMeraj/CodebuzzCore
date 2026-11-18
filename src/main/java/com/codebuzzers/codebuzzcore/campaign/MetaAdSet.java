package com.codebuzzers.codebuzzcore.campaign;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_ad_sets")
public class MetaAdSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "campaign_id", nullable = false) private Long campaignId;
    @Column(name = "meta_adset_id", nullable = false, length = 64) private String metaAdsetId;
    @Column(nullable = false) private String name;
    @Column(length = 64) private String status;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = java.time.Instant.now(); }

}
