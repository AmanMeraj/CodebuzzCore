package com.codebuzzers.codebuzzcore.meta;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_field_mappings")
public class MetaFieldMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false) private Long formId;
    @Column(name = "source_field", nullable = false, length = 128) private String sourceField;
    @Column(name = "target_field", nullable = false, length = 64) private String targetField;

}
