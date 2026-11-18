package com.codebuzzers.codebuzzcore.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {
    Optional<UsageRecord> findByTenantIdAndPeriodStartAndMetric(Long tenantId, LocalDate periodStart, String metric);

    @Modifying
    @Query(value = """
            INSERT INTO usage_records (tenant_id, period_start, period_end, metric, quantity)
            VALUES (:tenantId, :start, :end, :metric, 1)
            ON DUPLICATE KEY UPDATE quantity = quantity + 1
            """, nativeQuery = true)
    void increment(@Param("tenantId") long tenantId,
                   @Param("start") LocalDate start,
                   @Param("end") LocalDate end,
                   @Param("metric") String metric);
}
