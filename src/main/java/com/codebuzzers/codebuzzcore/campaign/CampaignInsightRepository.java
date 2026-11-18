package com.codebuzzers.codebuzzcore.campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface CampaignInsightRepository extends JpaRepository<CampaignInsight, Long> {
    List<CampaignInsight> findByTenantIdAndObjectTypeAndMetaObjectIdAndInsightDateBetween(Long tenantId, String objectType, String metaObjectId, LocalDate from, LocalDate to);
    boolean existsByTenantId(Long tenantId);
    Optional<CampaignInsight> findByTenantIdAndObjectTypeAndMetaObjectIdAndInsightDate(Long tenantId, String objectType, String metaObjectId, LocalDate insightDate);
    @Query("""
        select i from CampaignInsight i
        where i.tenantId = :tenantId and i.objectType = 'CAMPAIGN'
          and i.insightDate >= :from and i.insightDate <= :to
        """)
    List<CampaignInsight> campaignsBetween(@Param("tenantId") long tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}