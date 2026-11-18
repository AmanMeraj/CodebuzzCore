package com.codebuzzers.codebuzzcore.campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MetaAdRepository extends JpaRepository<MetaAd, Long> {
    Optional<MetaAd> findByTenantIdAndMetaAdId(Long tenantId, String metaAdId);
}
