package com.codebuzzers.codebuzzcore.workforce;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRecordRepository extends JpaRepository<LeaveRecord, Long> {

    Optional<LeaveRecord> findByPublicIdAndTenantId(String publicId, Long tenantId);

    List<LeaveRecord> findByTenantIdAndUserIdOrderByStartDateDesc(Long tenantId, Long userId);

    List<LeaveRecord> findByTenantIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long tenantId, String status, LocalDate onOrBefore, LocalDate onOrAfter);

    @Query("""
            select l from LeaveRecord l
            where l.tenantId = :tenantId and l.userId = :userId
              and l.status in ('PENDING', 'APPROVED')
              and l.startDate <= :endDate and l.endDate >= :startDate
            """)
    List<LeaveRecord> overlapping(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                  @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
