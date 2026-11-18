package com.codebuzzers.codebuzzcore.security;

import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.identity.AppRoleRepository;
import com.codebuzzers.codebuzzcore.identity.AppRole;
import com.codebuzzers.codebuzzcore.identity.AppUser;
import com.codebuzzers.codebuzzcore.identity.AppUserRepository;
import com.codebuzzers.codebuzzcore.identity.Tenant;
import com.codebuzzers.codebuzzcore.identity.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuthUserLoader {

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final AppRoleRepository roles;

    public AuthUserLoader(AppUserRepository users, TenantRepository tenants, AppRoleRepository roles) {
        this.users = users;
        this.tenants = tenants;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser load(String userPublicId, String viewTenantPublicId) {
        AppUser user = users.findByPublicId(userPublicId).orElse(null);
        if (user == null || !Codes.UserStatus.ACTIVE.equals(user.getStatus())) {
            return null;
        }
        Tenant home = tenants.findById(user.getTenantId()).orElse(null);
        if (home == null || !Codes.TenantStatus.ACTIVE.equals(home.getStatus())) {
            return null;
        }
        Set<String> permissions = users.permissionCodes(user.getId());
        Set<String> roleNames = users.roleNames(user.getId());
        if (viewTenantPublicId == null || viewTenantPublicId.isBlank()) {
            return new AuthenticatedUser(
                    user.getId(), user.getPublicId(), user.getStatus(), user.isPlatformStaff(),
                    home.getId(), home.getPublicId(), home.getStatus(),
                    home.getTimezone(), home.getCurrencyCode(), home.getLocale(),
                    permissions, roleNames, false, user.getId());
        }
        if (!user.isPlatformStaff() || !permissions.contains(Codes.Permission.PLATFORM_TENANT_MANAGE)) {
            return null;
        }
        Tenant target = tenants.findByPublicId(viewTenantPublicId).orElse(null);
        if (target == null || !Codes.TenantStatus.ACTIVE.equals(target.getStatus())) {
            return null;
        }
        AppRole viewer = roles.findByNameAndSystemRoleTrue("VIEWER").orElse(null);
        Set<String> viewerPermissions = viewer == null
                ? Set.of()
                : viewer.getPermissions().stream().map(p -> p.getCode()).collect(java.util.stream.Collectors.toSet());
        // Support view is read-only (Viewer) except Meta setup, which CodeBuzzers staff may do for a company.
        Set<String> supportPermissions = new java.util.HashSet<>(viewerPermissions);
        supportPermissions.add(Codes.Permission.META_MANAGE);
        return new AuthenticatedUser(
                user.getId(), user.getPublicId(), user.getStatus(), true,
                target.getId(), target.getPublicId(), target.getStatus(),
                target.getTimezone(), target.getCurrencyCode(), target.getLocale(),
                supportPermissions, Set.of("VIEWER"), true, user.getId());
    }
}