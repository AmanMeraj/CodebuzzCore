package com.codebuzzers.codebuzzcore.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "push_enabled", nullable = false) private boolean pushEnabled = true;
    @Column(name = "lead_assigned", nullable = false) private boolean leadAssigned = true;

}
