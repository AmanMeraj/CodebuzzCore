package com.codebuzzers.codebuzzcore.meta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MetaFormFieldRepository extends JpaRepository<MetaFormField, Long> {
    List<MetaFormField> findByFormId(Long formId);
    Optional<MetaFormField> findByFormIdAndMetaFieldKey(Long formId, String metaFieldKey);
    void deleteByFormId(Long formId);
}
