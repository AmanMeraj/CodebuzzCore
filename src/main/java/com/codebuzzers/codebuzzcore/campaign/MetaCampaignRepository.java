package com.codebuzzers.codebuzzcore.campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
public interface MetaCampaignRepository extends JpaRepository<MetaCampaign, Long> {
    List<MetaCampaign> findByTenantIdOrderByNameAsc(Long tenantId);
    List<MetaCampaign> findByTenantIdAndAdAccountIdInOrderByNameAsc(Long tenantId, Collection<Long> adAccountIds);
    Optional<MetaCampaign> findByTenantIdAndMetaCampaignId(Long tenantId, String metaCampaignId);
}
