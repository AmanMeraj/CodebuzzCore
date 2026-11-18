package com.codebuzzers.codebuzzcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByTeamIdAndTenantIdAndLeftAtIsNull(Long teamId, Long tenantId);

    Optional<TeamMember> findByTeamIdAndUserIdAndLeftAtIsNull(Long teamId, Long userId);

    @Query("select m.userId from TeamMember m where m.tenantId = :tenantId and m.teamId = :teamId and m.leftAt is null")
    List<Long> activeUserIds(@Param("tenantId") long tenantId, @Param("teamId") long teamId);
}
