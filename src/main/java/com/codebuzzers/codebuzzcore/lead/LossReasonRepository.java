package com.codebuzzers.codebuzzcore.lead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface LossReasonRepository extends JpaRepository<LossReason, Long> {
    List<LossReason> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
    Optional<LossReason> findByPublicIdAndTenantId(String publicId, Long tenantId);
}
