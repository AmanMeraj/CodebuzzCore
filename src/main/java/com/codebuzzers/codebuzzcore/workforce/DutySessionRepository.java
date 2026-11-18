package com.codebuzzers.codebuzzcore.workforce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DutySessionRepository extends JpaRepository<DutySession, Long> {

    Optional<DutySession> findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(Long userId);

    Optional<DutySession> findFirstByTenantIdAndUserIdOrderByStartedAtDesc(Long tenantId, Long userId);

    List<DutySession> findByTenantIdAndUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
            Long tenantId, Long userId, Instant from, Instant to);
}
