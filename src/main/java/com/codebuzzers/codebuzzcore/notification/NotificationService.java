package com.codebuzzers.codebuzzcore.notification;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotificationService {

    private final AppNotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final OutboxEventRepository outbox;

    public NotificationService(AppNotificationRepository notifications, NotificationPreferenceRepository preferences,
                               OutboxEventRepository outbox) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.outbox = outbox;
    }

    public void ensurePreference(long userId) {
        if (preferences.findByUserId(userId).isPresent()) {
            return;
        }
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setPushEnabled(true);
        preference.setLeadAssigned(true);
        preferences.save(preference);
    }

    @Transactional
    public void notifyUser(long tenantId, long userId, String title, String body, String deepLink, String dedupeKey) {
        if (dedupeKey != null && notifications.findByDedupeKey(dedupeKey).isPresent()) {
            return;
        }
        AppNotification notification = new AppNotification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setTitle(trim(title, 160));
        notification.setBody(trim(body, 500));
        notification.setDeepLink(deepLink);
        notification.setDedupeKey(dedupeKey);
        notification.setCreatedAt(Instant.now());
        try {
            notifications.saveAndFlush(notification);
        } catch (DataIntegrityViolationException ex) {
            return;
        }
        NotificationPreference preference = preferences.findByUserId(userId).orElse(null);
        if (preference != null && !preference.isPushEnabled()) {
            return;
        }
        if (dedupeKey != null && outbox.findByDedupeKey("push:" + dedupeKey).isPresent()) {
            return;
        }
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(tenantId);
        event.setEventType("PUSH");
        event.setDedupeKey(dedupeKey == null ? null : "push:" + dedupeKey);
        event.setPayloadJson("""
                {"notificationId":%d,"userId":%d,"title":%s,"body":%s,"deepLink":%s}
                """.formatted(
                notification.getId(),
                userId,
                json(notification.getTitle()),
                json(notification.getBody()),
                json(deepLink)).replace("\n", ""));
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setAvailableAt(Instant.now());
        event.setCreatedAt(Instant.now());
        try {
            outbox.save(event);
        } catch (DataIntegrityViolationException ignored) {
            // The push was already queued.
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
