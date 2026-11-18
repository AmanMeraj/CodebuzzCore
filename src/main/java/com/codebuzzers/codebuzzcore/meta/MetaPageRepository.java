package com.codebuzzers.codebuzzcore.meta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MetaPageRepository extends JpaRepository<MetaPage, Long> {
    List<MetaPage> findByTenantIdAndConnectionId(Long tenantId, Long connectionId);
    List<MetaPage> findByMetaPageIdAndStatus(String metaPageId, String status);
    Optional<MetaPage> findByTenantIdAndMetaPageId(Long tenantId, String metaPageId);
    List<MetaPage> findByTenantIdAndStatus(Long tenantId, String status);
}
