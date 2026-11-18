package com.codebuzzers.codebuzzcore.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenant_limits")
public class TenantLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_ad_accounts", nullable = false)
    private int maxAdAccounts;

    @Column(name = "max_leads_per_month", nullable = false)
    private int maxLeadsPerMonth;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
