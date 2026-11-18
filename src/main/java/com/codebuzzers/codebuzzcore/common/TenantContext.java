package com.codebuzzers.codebuzzcore.common;

import java.util.Set;

public final class TenantContext {

    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Ctx ctx) {
        CURRENT.set(ctx);
    }

    public static Ctx get() {
        return CURRENT.get();
    }

    public static Ctx require() {
        Ctx ctx = CURRENT.get();
        if (ctx == null) {
            throw ApiException.unauthorized("Authentication is required");
        }
        return ctx;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Ctx(
            long tenantId,
            String tenantPublicId,
            long userId,
            String userPublicId,
            String timezone,
            String currency,
            String locale,
            Set<String> permissions,
            Set<String> roles,
            boolean supportView,
            Long actorUserId
    ) {
        public boolean has(String permission) {
            return permissions.contains(permission);
        }
    }
}
