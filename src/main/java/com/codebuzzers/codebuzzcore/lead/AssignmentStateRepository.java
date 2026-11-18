package com.codebuzzers.codebuzzcore.lead;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssignmentStateRepository extends JpaRepository<AssignmentState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AssignmentState s where s.tenantId = :tenantId and s.teamId = :teamId")
    Optional<AssignmentState> lockTeam(@Param("tenantId") long tenantId, @Param("teamId") long teamId);
}
