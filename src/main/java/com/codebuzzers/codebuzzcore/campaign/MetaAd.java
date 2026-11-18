package com.codebuzzers.codebuzzcore.campaign;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_ads")
public class MetaAd {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "ad_set_id", nullable = false) private Long adSetId;
    @Column(name = "meta_ad_id", nullable = false, length = 64) private String metaAdId;
    @Column(nullable = false) private String name;
    @Column(length = 64) private String status;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = java.time.Instant.now(); }

}
