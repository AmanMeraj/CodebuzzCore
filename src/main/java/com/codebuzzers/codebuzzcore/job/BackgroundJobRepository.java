package com.codebuzzers.codebuzzcore.job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {
    Optional<BackgroundJob> findByJobKey(String jobKey);
    long countByStatus(String status);
    @Query(value = """
        SELECT id FROM background_jobs
        WHERE status = 'PENDING' AND available_at <= UTC_TIMESTAMP(6)
        ORDER BY id
        LIMIT 8
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> lockPendingIds();
    @Modifying
    @Query("""
        update BackgroundJob j
           set j.status = 'RUNNING', j.lockedAt = :now, j.lockedBy = :worker, j.attempts = j.attempts + 1, j.updatedAt = :now
         where j.id in :ids and j.status = 'PENDING'
        """)
    int claim(@Param("ids") List<Long> ids, @Param("now") Instant now, @Param("worker") String worker);
}
