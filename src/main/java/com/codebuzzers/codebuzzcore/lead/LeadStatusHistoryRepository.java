package com.codebuzzers.codebuzzcore.lead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LeadStatusHistoryRepository extends JpaRepository<LeadStatusHistory, Long> {
    List<LeadStatusHistory> findByTenantIdAndLeadIdOrderByChangedAtAsc(Long tenantId, Long leadId);
}
