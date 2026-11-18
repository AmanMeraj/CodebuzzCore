package com.codebuzzers.codebuzzcore.campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MetaAdSetRepository extends JpaRepository<MetaAdSet, Long> {
    List<MetaAdSet> findByTenantIdAndCampaignId(Long tenantId, Long campaignId);
    Optional<MetaAdSet> findByTenantIdAndMetaAdsetId(Long tenantId, String metaAdsetId);
}