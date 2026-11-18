package com.codebuzzers.codebuzzcore.meta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MetaFormRepository extends JpaRepository<MetaForm, Long> {
    List<MetaForm> findByTenantId(Long tenantId);
    Optional<MetaForm> findByTenantIdAndMetaFormId(Long tenantId, String metaFormId);
    List<MetaForm> findByPageId(Long pageId);
}
