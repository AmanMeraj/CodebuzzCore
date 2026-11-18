package com.codebuzzers.codebuzzcore.meta;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_form_fields")
public class MetaFormField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false) private Long formId;
    @Column(name = "meta_field_key", nullable = false, length = 128) private String metaFieldKey;
    @Column(nullable = false) private String label;
    @Column(name = "field_type", length = 64) private String fieldType;

}
