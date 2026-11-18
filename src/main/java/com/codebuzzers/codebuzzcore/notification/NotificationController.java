package com.codebuzzers.codebuzzcore.notification;

import com.codebuzzers.codebuzzcore.common.PageResponse;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final AppNotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final PushDeviceRepository devices;

    public NotificationController(AppNotificationRepository notifications, NotificationPreferenceRepository preferences,
                                  PushDeviceRepository devices) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.devices = devices;
    }

    @GetMapping
    public PageResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        TenantContext.Ctx ctx = TenantContext.require();
        var result = notifications.findByTenantIdAndUserIdOrderByCreatedAtDesc(ctx.tenantId(), ctx.userId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return new PageResponse<>(result.getContent().stream().map(row -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", row.getId());
            body.put("title", row.getTitle());
            body.put("body", row.getBody());
            body.put("deepLink", row.getDeepLink());
            body.put("readAt", row.getReadAt());
            body.put("createdAt", row.getCreatedAt());
            return body;
        }).toList(), page, size, result.getTotalElements());
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> read(@PathVariable long id) {
        TenantContext.Ctx ctx = TenantContext.require();
        AppNotification notification = notifications.findByIdAndTenantIdAndUserId(id, ctx.tenantId(), ctx.userId())
                .orElseThrow(() -> com.codebuzzers.codebuzzcore.common.ApiException.notFound("Notification not found"));
        notification.setReadAt(Instant.now());
        return Map.of("ok", true);
    }

    @PostMapping("/devices")
    public Map<String, Object> device(@RequestBody DeviceRequest request) {
        TenantContext.Ctx ctx = TenantContext.require();
        PushDevice device = devices.findByToken(request.token()).orElseGet(PushDevice::new);
        device.setTenantId(ctx.tenantId());
        device.setUserId(ctx.userId());
        device.setToken(request.token());
        device.setPlatform(request.platform() == null ? "ANDROID" : request.platform());
        device.setStatus("ACTIVE");
        device.setLastSeenAt(Instant.now());
        devices.save(device);
        return Map.of("ok", true);
    }

    @GetMapping("/preferences")
    public Map<String, Object> preferences() {
        TenantContext.Ctx ctx = TenantContext.require();
        NotificationPreference preference = preferences.findByUserId(ctx.userId()).orElse(null);
        return Map.of("pushEnabled", preference == null || preference.isPushEnabled(),
                "leadAssigned", preference == null || preference.isLeadAssigned());
    }

    @PostMapping("/preferences")
    public Map<String, Object> updatePreferences(@RequestBody PreferenceRequest request) {
        TenantContext.Ctx ctx = TenantContext.require();
        NotificationPreference preference = preferences.findByUserId(ctx.userId()).orElseGet(NotificationPreference::new);
        preference.setUserId(ctx.userId());
        preference.setPushEnabled(request.pushEnabled());
        preference.setLeadAssigned(request.leadAssigned());
        preferences.save(preference);
        return Map.of("ok", true);
    }

    public record DeviceRequest(String token, String platform) {
    }

    public record PreferenceRequest(boolean pushEnabled, boolean leadAssigned) {
    }
}
