package com.codebuzzers.codebuzzcore.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {
    Optional<PushDevice> findByToken(String token);
    List<PushDevice> findByUserIdAndStatus(Long userId, String status);
}
