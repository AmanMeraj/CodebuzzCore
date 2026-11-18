package com.codebuzzers.codebuzzcore.meta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MetaFieldMappingRepository extends JpaRepository<MetaFieldMapping, Long> {
    List<MetaFieldMapping> findByFormId(Long formId);
    Optional<MetaFieldMapping> findByFormIdAndSourceField(Long formId, String sourceField);
    void deleteByFormId(Long formId);
}
