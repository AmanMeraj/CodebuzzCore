package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "loss_reasons")
public class LossReason {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36) private String publicId;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private boolean active = true;
    @PrePersist void pre() { if (publicId == null) publicId = java.util.UUID.randomUUID().toString(); }

}
