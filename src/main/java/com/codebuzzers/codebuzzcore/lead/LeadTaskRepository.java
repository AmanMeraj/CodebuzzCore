package com.codebuzzers.codebuzzcore.lead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface LeadTaskRepository extends JpaRepository<LeadTask, Long> {
    Optional<LeadTask> findByPublicIdAndTenantId(String publicId, Long tenantId);
    List<LeadTask> findByTenantIdAndAssignedUserIdAndStatusOrderByDueAtAsc(Long tenantId, Long assignedUserId, String status);
    List<LeadTask> findByTenantIdAndLeadIdOrderByDueAtAsc(Long tenantId, Long leadId);
}
