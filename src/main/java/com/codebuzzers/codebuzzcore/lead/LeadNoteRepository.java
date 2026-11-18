package com.codebuzzers.codebuzzcore.lead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LeadNoteRepository extends JpaRepository<LeadNote, Long> {
    List<LeadNote> findByTenantIdAndLeadIdOrderByCreatedAtAsc(Long tenantId, Long leadId);
}
