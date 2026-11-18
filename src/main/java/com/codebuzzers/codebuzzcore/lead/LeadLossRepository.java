package com.codebuzzers.codebuzzcore.lead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LeadLossRepository extends JpaRepository<LeadLoss, Long> {
    Optional<LeadLoss> findByLeadId(Long leadId);
}
