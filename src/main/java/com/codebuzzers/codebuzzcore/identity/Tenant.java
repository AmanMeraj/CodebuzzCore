package com.codebuzzers.codebuzzcore.identity;

import com.codebuzzers.codebuzzcore.common.PublicEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenants")
public class Tenant extends PublicEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "default_team_id")
    private Long defaultTeamId;

    @Column(name = "assignment_timeout_minutes", nullable = false)
    private int assignmentTimeoutMinutes = 15;

    @Column(name = "lead_retention_days")
    private Integer leadRetentionDays;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
