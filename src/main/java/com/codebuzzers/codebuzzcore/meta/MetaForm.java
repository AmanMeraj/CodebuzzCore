package com.codebuzzers.codebuzzcore.meta;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_forms")
public class MetaForm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "page_id", nullable = false) private Long pageId;
    @Column(name = "meta_form_id", nullable = false, length = 64) private String metaFormId;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 32) private String status;
    @Column(nullable = false) private int version = 1;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @PrePersist void prePersist() { java.time.Instant now = java.time.Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = java.time.Instant.now(); }

}
