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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_ad_accounts", nullable = false)
    private int maxAdAccounts;

    @Column(name = "max_leads_per_month", nullable = false)
    private int maxLeadsPerMonth;

    @Column(nullable = false)
    private boolean active;
}
