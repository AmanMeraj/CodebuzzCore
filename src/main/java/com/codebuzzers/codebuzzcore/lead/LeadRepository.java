package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {
    List<Lead> findByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long tenantId, Instant from, Instant to);
    List<Lead> findByTenantIdAndMetaCampaignIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long tenantId, String metaCampaignId, Instant from, Instant to);
    List<Lead> findByTenantIdAndAssignedUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long tenantId, Long assignedUserId, Instant from, Instant to);
    List<Lead> findTop50ByStatusAndAssignedUserIdIsNullAndTestFalseAndSpamFalseOrderByCreatedAtDesc(String status);
    boolean existsByTenantId(Long tenantId);
    List<Lead> findByTenantIdAndSpamTrueAndNameIsNullAndPhoneNormalizedIsNullAndEmailNormalizedIsNull(Long tenantId);
    Optional<Lead> findByPublicIdAndTenantId(String publicId, Long tenantId);
    Optional<Lead> findByTenantIdAndMetaLeadId(Long tenantId, String metaLeadId);
    boolean existsByTenantIdAndPhoneNormalizedAndMetaLeadIdNot(Long tenantId, String phoneNormalized, String metaLeadId);
    List<Lead> findByTenantIdAndAssignedUserIdAndStatusIn(Long tenantId, Long assignedUserId, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lead l where l.id = :id and l.tenantId = :tenantId")
    Optional<Lead> lockById(@Param("id") long id, @Param("tenantId") long tenantId);
}