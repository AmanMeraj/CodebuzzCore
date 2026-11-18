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
@Table(name = "leads")
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36) private String publicId;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "meta_lead_id", nullable = false, length = 100) private String metaLeadId;
    @Column(name = "meta_page_id", length = 100) private String metaPageId;
    @Column(name = "meta_form_id", length = 100) private String metaFormId;
    @Column(name = "meta_ad_account_id", length = 100) private String metaAdAccountId;
    @Column(name = "meta_campaign_id", length = 100) private String metaCampaignId;
    @Column(name = "meta_adset_id", length = 100) private String metaAdsetId;
    @Column(name = "meta_ad_id", length = 100) private String metaAdId;
    @Column private String name;
    @Column(name = "phone_normalized", length = 32) private String phoneNormalized;
    @Column(name = "email_normalized", length = 320) private String emailNormalized;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "assigned_user_id") private Long assignedUserId;
    @Column(name = "assigned_team_id") private Long assignedTeamId;
    @Column(name = "first_response_at") private java.time.Instant firstResponseAt;
    @Column(name = "last_contact_at") private java.time.Instant lastContactAt;
    @Column(name = "is_test", nullable = false) private boolean test;
    @Column(name = "is_spam", nullable = false) private boolean spam;
    @Column(name = "over_quota", nullable = false) private boolean overQuota;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload") private String rawPayload;
    @Column(name = "meta_created_at") private java.time.Instant metaCreatedAt;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist void pre() {
        java.time.Instant now = java.time.Instant.now();
        if (publicId == null) publicId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void upd() { updatedAt = java.time.Instant.now(); }

}
