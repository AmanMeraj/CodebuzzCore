package com.codebuzzers.codebuzzcore.lead;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LeadAssignmentRepository extends JpaRepository<LeadAssignment, Long> {
    Optional<LeadAssignment> findFirstByLeadIdAndTenantIdOrderByAttemptNoDesc(Long leadId, Long tenantId);
    List<LeadAssignment> findByTenantIdAndLeadIdOrderByAssignedAtAsc(Long tenantId, Long leadId);
    long countByTenantIdAndToUserIdAndTimedOutTrueAndAssignedAtGreaterThanEqualAndAssignedAtLessThan(Long tenantId, Long toUserId, Instant from, Instant to);

    @Query("""
        select a from LeadAssignment a
        where a.timedOut = false and a.respondedAt is null and a.responseDeadline < :now
        order by a.id
        """)
    List<LeadAssignment> dueTimeouts(@Param("now") Instant now);
}
