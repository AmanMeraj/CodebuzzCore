package com.codebuzzers.codebuzzcore.security;

import java.util.Set;

public record AuthenticatedUser(
        long userId,
        String userPublicId,
        String userStatus,
        boolean platformStaff,
        long tenantId,
        String tenantPublicId,
        String tenantStatus,
        String timezone,
        String currency,
        String locale,
        Set<String> permissions,
        Set<String> roles,
        boolean supportView,
        Long actorUserId
) {
}
