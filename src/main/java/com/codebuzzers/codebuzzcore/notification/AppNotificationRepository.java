package com.codebuzzers.codebuzzcore.notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    Page<AppNotification> findByTenantIdAndUserIdOrderByCreatedAtDesc(Long tenantId, Long userId, Pageable pageable);
    Optional<AppNotification> findByIdAndTenantIdAndUserId(Long id, Long tenantId, Long userId);
    Optional<AppNotification> findByDedupeKey(String dedupeKey);
}
