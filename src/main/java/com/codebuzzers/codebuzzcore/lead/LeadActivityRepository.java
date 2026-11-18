package com.codebuzzers.codebuzzcore.lead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
public interface LeadActivityRepository extends JpaRepository<LeadActivity, Long> {
    List<LeadActivity> findByTenantIdAndLeadIdOrderByOccurredAtAsc(Long tenantId, Long leadId);
    long countByTenantIdAndUserIdAndTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(Long tenantId, Long userId, String type, Instant from, Instant to);
}
