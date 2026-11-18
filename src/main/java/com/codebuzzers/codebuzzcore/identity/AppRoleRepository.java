package com.codebuzzers.codebuzzcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    Optional<AppRole> findByNameAndSystemRoleTrue(String name);
}
