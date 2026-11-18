package com.codebuzzers.codebuzzcore.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "push_devices")
public class PushDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 512) private String token;
    @Column(nullable = false, length = 32) private String platform;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "last_seen_at", nullable = false) private java.time.Instant lastSeenAt;

}
