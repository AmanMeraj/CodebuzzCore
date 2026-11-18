package com.codebuzzers.codebuzzcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByPublicIdAndTenantId(String publicId, Long tenantId);

    List<Team> findByTenantIdOrderByNameAsc(Long tenantId);

    @Query("""
            select t.id from Team t
            where t.tenantId = :tenantId
              and (t.managerUserId = :userId
                   or t.id in (select m.teamId from TeamMember m where m.userId = :userId and m.leftAt is null))
            """)
    List<Long> teamIdsForUser(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
