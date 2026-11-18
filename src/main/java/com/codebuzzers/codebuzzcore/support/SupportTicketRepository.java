package com.codebuzzers.codebuzzcore.support;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    Optional<SupportTicket> findByPublicIdAndTenantId(String publicId, Long tenantId);
}
