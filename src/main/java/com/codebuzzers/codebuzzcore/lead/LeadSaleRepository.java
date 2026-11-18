package com.codebuzzers.codebuzzcore.lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
public interface LeadSaleRepository extends JpaRepository<LeadSale, Long> {
    Optional<LeadSale> findByLeadId(Long leadId);
    @Query("select coalesce(sum(s.amountMinor), 0) from LeadSale s where s.tenantId = :tenantId and s.wonAt >= :from and s.wonAt < :to")
    long sumAmount(@Param("tenantId") long tenantId, @Param("from") Instant from, @Param("to") Instant to);
}
