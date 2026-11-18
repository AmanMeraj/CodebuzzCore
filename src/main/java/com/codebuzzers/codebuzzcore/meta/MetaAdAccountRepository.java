package com.codebuzzers.codebuzzcore.meta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MetaAdAccountRepository extends JpaRepository<MetaAdAccount, Long> {
    List<MetaAdAccount> findByTenantIdAndConnectionId(Long tenantId, Long connectionId);
    Optional<MetaAdAccount> findByTenantIdAndMetaAdAccountId(Long tenantId, String metaAdAccountId);
    List<MetaAdAccount> findByTenantIdAndStatus(Long tenantId, String status);
    long countByTenantIdAndStatus(Long tenantId, String status);
}
