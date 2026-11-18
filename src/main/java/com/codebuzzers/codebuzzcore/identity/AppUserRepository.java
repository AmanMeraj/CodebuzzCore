package com.codebuzzers.codebuzzcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByPublicId(String publicId);

    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByTenantIdOrderByNameAsc(Long tenantId);

    long countByTenantIdAndStatus(Long tenantId, String status);

    @Query("select p.code from AppUser u join u.roles r join r.permissions p where u.id = :userId")
    Set<String> permissionCodes(@Param("userId") long userId);

    @Query("select r.name from AppUser u join u.roles r where u.id = :userId")
    Set<String> roleNames(@Param("userId") long userId);

    @Query("""
            select u.id from AppUser u
            where u.tenantId = :tenantId and u.status = 'ACTIVE' and u.onDuty = true and u.id in :userIds
            order by u.id
            """)
    List<Long> eligibleIds(@Param("tenantId") long tenantId, @Param("userIds") List<Long> userIds);

    @Query("""
            select u.id from AppUser u join u.roles r join r.permissions p
            where u.tenantId = :tenantId and u.status = 'ACTIVE' and p.code = :permission
            """)
    List<Long> idsWithPermission(@Param("tenantId") long tenantId, @Param("permission") String permission);
}
