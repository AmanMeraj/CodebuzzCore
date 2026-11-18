package com.codebuzzers.codebuzzcore.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantLimitRepository extends JpaRepository<TenantLimit, Long> {
    Optional<TenantLimit> findByTenantId(Long tenantId);
}
