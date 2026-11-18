package com.codebuzzers.codebuzzcore.meta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MetaConnectionRepository extends JpaRepository<MetaConnection, Long> {
    Optional<MetaConnection> findByPublicIdAndTenantId(String publicId, Long tenantId);
    Optional<MetaConnection> findFirstByTenantIdAndStatusOrderByIdDesc(Long tenantId, String status);
    List<MetaConnection> findByTenantIdOrderByIdDesc(Long tenantId);
    List<MetaConnection> findByStatus(String status);
}
