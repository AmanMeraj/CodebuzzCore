package com.codebuzzers.codebuzzcore.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    Optional<OutboxEvent> findByDedupeKey(String dedupeKey);
    @Query(value = """
        SELECT id FROM outbox_events
        WHERE status = 'PENDING' AND available_at <= UTC_TIMESTAMP(6)
        ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> lockPendingIds();
}
