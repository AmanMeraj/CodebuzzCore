package com.codebuzzers.codebuzzcore.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class AppNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 500) private String body;
    @Column(name = "deep_link") private String deepLink;
    @Column(name = "dedupe_key", length = 160) private String dedupeKey;
    @Column(name = "read_at") private java.time.Instant readAt;
    @Column(name = "created_at", nullable = false) private java.time.Instant createdAt;

}
