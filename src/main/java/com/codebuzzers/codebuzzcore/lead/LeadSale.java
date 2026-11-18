package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lead_sales")
public class LeadSale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "lead_id", nullable = false) private Long leadId;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "won_at", nullable = false) private java.time.Instant wonAt;

}
