package com.codebuzzers.codebuzzcore.identity;

import com.codebuzzers.codebuzzcore.audit.AuditService;
import com.codebuzzers.codebuzzcore.billing.EntitlementService;
import com.codebuzzers.codebuzzcore.billing.Plan;
import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.AppProperties;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.Hashes;
import com.codebuzzers.codebuzzcore.common.PhoneNormalizer;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.common.WebRequests;
import com.codebuzzers.codebuzzcore.lead.AssignmentService;
import com.codebuzzers.codebuzzcore.lead.AssignmentState;
import com.codebuzzers.codebuzzcore.lead.AssignmentStateRepository;
import com.codebuzzers.codebuzzcore.lead.LossReason;
import com.codebuzzers.codebuzzcore.lead.LossReasonRepository;
import com.codebuzzers.codebuzzcore.notification.NotificationService;
import com.codebuzzers.codebuzzcore.security.AccessGuard;
import com.codebuzzers.codebuzzcore.security.JwtService;
import com.codebuzzers.codebuzzcore.security.LoginRateLimiter;
import com.codebuzzers.codebuzzcore.workforce.WorkforceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("OWNER", "MANAGER", "EMPLOYEE", "VIEWER");
    private static final List<String> DEFAULT_LOSS_REASONS = List.of(
            "Price", "Not interested", "No response", "Bought elsewhere", "Invalid lead", "Other");

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final LossReasonRepository lossReasons;
    private final AssignmentStateRepository assignmentStates;
    private final EntitlementService entitlements;
    private final NotificationService notifications;
    private final AssignmentService assignmentService;
    private final AuditService audit;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final LoginRateLimiter rateLimiter;
    private final AccessGuard access;
    private final AppProperties properties;
    private final WorkforceService workforce;

    public AccountService(TenantRepository tenants, AppUserRepository users, AppRoleRepository roles,
                          TeamRepository teams, TeamMemberRepository members, RefreshTokenRepository refreshTokens,
                          PasswordResetTokenRepository resetTokens, LossReasonRepository lossReasons,
                          AssignmentStateRepository assignmentStates, EntitlementService entitlements,
                          NotificationService notifications, AssignmentService assignmentService, AuditService audit,
                          PasswordEncoder passwords, JwtService jwt, LoginRateLimiter rateLimiter, AccessGuard access,
                          AppProperties properties, WorkforceService workforce) {
        this.tenants = tenants;
        this.users = users;
        this.roles = roles;
        this.teams = teams;
        this.members = members;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.lossReasons = lossReasons;
        this.assignmentStates = assignmentStates;
        this.entitlements = entitlements;
        this.notifications = notifications;
        this.assignmentService = assignmentService;
        this.audit = audit;
        this.passwords = passwords;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
        this.access = access;
        this.properties = properties;
        this.workforce = workforce;
    }

    @Transactional
    public Map<String, Object> register(String companyName, String countryCode, String timezone, String currency,
                                        String ownerName, String email, String password) {
        String normalizedEmail = PhoneNormalizer.email(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }
        ZoneId.of(timezone);
        Tenant tenant = new Tenant();
        tenant.setName(companyName.trim());
        tenant.setStatus(Codes.TenantStatus.ACTIVE);
        tenant.setCountryCode(countryCode.trim().toUpperCase(Locale.ROOT));
        tenant.setTimezone(timezone.trim());
        tenant.setCurrencyCode(currency.trim().toUpperCase(Locale.ROOT));
        tenant.setLocale("en");
        tenant.setAssignmentTimeoutMinutes(15);
        tenants.save(tenant);

        AppUser owner = newUser(tenant.getId(), ownerName, normalizedEmail, password, "OWNER");
        owner.setOnDuty(true);
        users.save(owner);
        workforce.dutyChanged(tenant.getId(), owner.getId(), true);
        notifications.ensurePreference(owner.getId());
        for (String reason : DEFAULT_LOSS_REASONS) {
            LossReason row = new LossReason();
            row.setTenantId(tenant.getId());
            row.setName(reason);
            row.setActive(true);
            lossReasons.save(row);
        }
        Plan plan = entitlements.requirePlan("BASIC");
        entitlements.subscribe(tenant.getId(), plan, tenant.getTimezone());
        audit.record(tenant.getId(), owner.getId(), "TENANT_CREATED", "TENANT", tenant.getPublicId(), null,
                "{\"plan\":\"BASIC\"}", Codes.Source.ANDROID);
        return tokens(owner, null);
    }

    @Transactional
    public Map<String, Object> login(String email, String password) {
        String normalizedEmail = PhoneNormalizer.email(email);
        rateLimiter.check("login:" + normalizedEmail);
        AppUser user = users.findByEmail(normalizedEmail).orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!Codes.UserStatus.ACTIVE.equals(user.getStatus())) {
            throw ApiException.forbidden("This user is disabled");
        }
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!Codes.TenantStatus.ACTIVE.equals(tenant.getStatus())) {
            throw ApiException.forbidden("This company is not active");
        }
        user.setLastLoginAt(Instant.now());
        audit.record(tenant.getId(), user.getId(), "LOGIN", "USER", user.getPublicId(), null, null, Codes.Source.ANDROID);
        return tokens(user, null);
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(jwt.hash(refreshToken)).orElseThrow(() -> ApiException.unauthorized("Refresh token is invalid"));
        if (stored.getRevokedAt() != null) {
            refreshTokens.revokeAll(stored.getUserId(), Instant.now());
            throw ApiException.unauthorized("Refresh token was already used");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token has expired");
        }
        stored.setRevokedAt(Instant.now());
        AppUser user = users.findById(stored.getUserId()).orElseThrow(() -> ApiException.unauthorized("Refresh token is invalid"));
        Map<String, Object> issued = tokens(user, null);
        stored.setReplacedById(refreshTokens.findByTokenHash(jwt.hash(String.valueOf(issued.get("refreshToken")))).map(RefreshToken::getId).orElse(null));
        return issued;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(jwt.hash(refreshToken)).ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        TenantContext.Ctx ctx = TenantContext.require();
        AppUser user = users.findById(ctx.userId()).orElseThrow(() -> ApiException.unauthorized("Authentication is required"));
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is wrong");
        }
        user.setPasswordHash(passwords.encode(newPassword));
        refreshTokens.revokeAll(user.getId(), Instant.now());
        audit.record(ctx.tenantId(), ctx.userId(), "PASSWORD_CHANGED", "USER", user.getPublicId(), null, null, Codes.Source.ANDROID);
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = PhoneNormalizer.email(email);
        rateLimiter.check("reset:" + normalizedEmail);
        users.findByEmail(normalizedEmail).ifPresent(user -> {
            PasswordResetToken token = new PasswordResetToken();
            String raw = Hashes.randomToken();
            token.setUserId(user.getId());
            token.setTokenHash(Hashes.sha256(raw));
            token.setExpiresAt(Instant.now().plusSeconds(30 * 60));
            token.setCreatedAt(Instant.now());
            resetTokens.save(token);
            if (properties.dev().exposeResetToken()) {
                log.warn("Password reset token for {} is {} (dev only)", user.getPublicId(), raw);
            }
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        rateLimiter.check("reset-token");
        PasswordResetToken token = resetTokens.findByTokenHash(Hashes.sha256(rawToken))
                .orElseThrow(() -> ApiException.badRequest("Reset token is invalid"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Reset token is invalid");
        }
        AppUser user = users.findById(token.getUserId()).orElseThrow(() -> ApiException.badRequest("Reset token is invalid"));
        user.setPasswordHash(passwords.encode(newPassword));
        token.setUsedAt(Instant.now());
        refreshTokens.revokeAll(user.getId(), Instant.now());
        audit.record(user.getTenantId(), user.getId(), "PASSWORD_RESET", "USER", user.getPublicId(), null, null, Codes.Source.ANDROID);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        TenantContext.Ctx ctx = TenantContext.require();
        AppUser user = users.findById(ctx.userId()).orElseThrow(() -> ApiException.unauthorized("Authentication is required"));
        Tenant tenant = tenants.findById(ctx.tenantId()).orElseThrow();
        Plan plan = entitlements.currentPlan(ctx.tenantId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", userView(user));
        body.put("tenant", tenantView(tenant));
        body.put("roles", ctx.roles());
        body.put("permissions", ctx.permissions());
        body.put("plan", plan.getCode());
        body.put("features", entitlements.featureCodes(ctx.tenantId()));
        body.put("supportView", ctx.supportView());
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> company() {
        TenantContext.Ctx ctx = TenantContext.require();
        return tenantView(tenants.findById(ctx.tenantId()).orElseThrow());
    }

    @Transactional
    public Map<String, Object> updateCompany(String name, String timezone, String currency, Integer timeoutMinutes, Integer retentionDays) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.USER_MANAGE);
        Tenant tenant = tenants.findById(ctx.tenantId()).orElseThrow();
        if (name != null && !name.isBlank()) {
            tenant.setName(name.trim());
        }
        if (timezone != null && !timezone.isBlank()) {
            ZoneId.of(timezone.trim());
            tenant.setTimezone(timezone.trim());
        }
        if (currency != null && !currency.isBlank()) {
            tenant.setCurrencyCode(currency.trim().toUpperCase(Locale.ROOT));
        }
        if (timeoutMinutes != null) {
            tenant.setAssignmentTimeoutMinutes(Math.min(Math.max(timeoutMinutes, 5), 24 * 60));
        }
        if (retentionDays != null) {
            tenant.setLeadRetentionDays(retentionDays < 1 ? null : retentionDays);
        }
        audit.record(ctx.tenantId(), ctx.userId(), "COMPANY_UPDATED", "TENANT", tenant.getPublicId(), null, null, Codes.Source.ANDROID);
        return tenantView(tenant);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsers() {
        TenantContext.Ctx ctx = access.requireAny(Codes.Permission.USER_MANAGE, Codes.Permission.TEAM_MANAGE, Codes.Permission.LEAD_ASSIGN);
        return users.findByTenantIdOrderByNameAsc(ctx.tenantId()).stream().map(this::userView).toList();
    }

    @Transactional
    public Map<String, Object> createUser(String name, String email, String password, String roleName) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.USER_MANAGE);
        if (!ASSIGNABLE_ROLES.contains(roleName)) {
            throw ApiException.badRequest("Role must be OWNER, MANAGER, EMPLOYEE, or VIEWER");
        }
        long active = users.countByTenantIdAndStatus(ctx.tenantId(), Codes.UserStatus.ACTIVE);
        entitlements.requireUserSlot(ctx.tenantId(), active);
        String normalizedEmail = PhoneNormalizer.email(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser user = newUser(ctx.tenantId(), name, normalizedEmail, password, roleName);
        users.saveAndFlush(user);
        notifications.ensurePreference(user.getId());
        audit.record(ctx.tenantId(), ctx.userId(), "USER_CREATED", "USER", user.getPublicId(), null,
                "{\"role\":\"" + roleName + "\"}", Codes.Source.ANDROID);
        return userView(user);
    }

    @Transactional
    public Map<String, Object> setDuty(String publicId, boolean onDuty) {
        TenantContext.Ctx ctx = TenantContext.require();
        AppUser user = users.findByPublicId(publicId).filter(row -> row.getTenantId().equals(ctx.tenantId()))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!user.getId().equals(ctx.userId()) && !ctx.has(Codes.Permission.USER_MANAGE)) {
            throw ApiException.forbidden("You cannot change this user's duty status");
        }
        user.setOnDuty(onDuty);
        workforce.dutyChanged(ctx.tenantId(), user.getId(), onDuty);
        return userView(user);
    }

    @Transactional
    public void disableUser(String publicId) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.USER_MANAGE);
        AppUser user = users.findByPublicId(publicId).filter(row -> row.getTenantId().equals(ctx.tenantId()))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getId().equals(ctx.userId())) {
            throw ApiException.badRequest("You cannot disable your own user");
        }
        user.setStatus(Codes.UserStatus.DISABLED);
        user.setOnDuty(false);
        workforce.dutyChanged(ctx.tenantId(), user.getId(), false);
        refreshTokens.revokeAll(user.getId(), Instant.now());
        assignmentService.reassignOpenLeads(ctx.tenantId(), user.getId());
        audit.record(ctx.tenantId(), ctx.userId(), "USER_DISABLED", "USER", user.getPublicId(), null, null, Codes.Source.ANDROID);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeams() {
        TenantContext.Ctx ctx = access.requireAny(Codes.Permission.TEAM_MANAGE, Codes.Permission.LEAD_ASSIGN, Codes.Permission.LEAD_VIEW_TEAM);
        return teams.findByTenantIdOrderByNameAsc(ctx.tenantId()).stream().map(this::teamView).toList();
    }

    @Transactional
    public Map<String, Object> createTeam(String name, String managerPublicId) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.TEAM_MANAGE);
        Team team = new Team();
        team.setTenantId(ctx.tenantId());
        team.setName(name.trim());
        team.setStatus("ACTIVE");
        if (managerPublicId != null && !managerPublicId.isBlank()) {
            team.setManagerUserId(requireTenantUser(ctx.tenantId(), managerPublicId).getId());
        }
        teams.save(team);
        AssignmentState state = new AssignmentState();
        state.setTenantId(ctx.tenantId());
        state.setTeamId(team.getId());
        state.setVersion(0);
        assignmentStates.save(state);
        Tenant tenant = tenants.findById(ctx.tenantId()).orElseThrow();
        if (tenant.getDefaultTeamId() == null) {
            tenant.setDefaultTeamId(team.getId());
        }
        audit.record(ctx.tenantId(), ctx.userId(), "TEAM_CREATED", "TEAM", team.getPublicId(), null, null, Codes.Source.ANDROID);
        return teamView(team);
    }

    @Transactional
    public void addMember(String teamPublicId, String userPublicId) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.TEAM_MANAGE);
        Team team = requireTeam(ctx.tenantId(), teamPublicId);
        AppUser user = requireTenantUser(ctx.tenantId(), userPublicId);
        if (members.findByTeamIdAndUserIdAndLeftAtIsNull(team.getId(), user.getId()).isPresent()) {
            return;
        }
        TeamMember member = new TeamMember();
        member.setTenantId(ctx.tenantId());
        member.setTeamId(team.getId());
        member.setUserId(user.getId());
        member.setJoinedAt(Instant.now());
        members.save(member);
    }

    @Transactional
    public void removeMember(String teamPublicId, String userPublicId) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.TEAM_MANAGE);
        Team team = requireTeam(ctx.tenantId(), teamPublicId);
        AppUser user = requireTenantUser(ctx.tenantId(), userPublicId);
        members.findByTeamIdAndUserIdAndLeftAtIsNull(team.getId(), user.getId()).ifPresent(member -> member.setLeftAt(Instant.now()));
    }

    @Transactional
    public Map<String, Object> viewAs(String tenantPublicId) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.PLATFORM_TENANT_MANAGE);
        Tenant target = tenants.findByPublicId(tenantPublicId).orElseThrow(() -> ApiException.notFound("Tenant not found"));
        audit.record(target.getId(), ctx.actorUserId(), "PLATFORM_VIEW_AS", "TENANT", target.getPublicId(), null, null, Codes.Source.PLATFORM);
        AppUser actor = users.findById(ctx.actorUserId()).orElseThrow();
        String accessToken = jwt.accessToken(actor.getPublicId(), target.getPublicId(), properties.jwt().accessTtl());
        return Map.of("accessToken", accessToken, "expiresInSeconds", properties.jwt().accessTtl().toSeconds(), "tenantId", target.getPublicId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> platformTenants() {
        access.require(Codes.Permission.PLATFORM_TENANT_MANAGE);
        return tenants.findAll().stream().map(tenant -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", tenant.getPublicId());
            row.put("name", tenant.getName());
            row.put("status", tenant.getStatus());
            row.put("countryCode", tenant.getCountryCode());
            return row;
        }).toList();
    }

    private AppUser newUser(long tenantId, String name, String email, String password, String roleName) {
        AppRole role = roles.findByNameAndSystemRoleTrue(roleName).orElseThrow(() -> ApiException.badRequest("Role is not configured"));
        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setName(name.trim());
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(password));
        user.setStatus(Codes.UserStatus.ACTIVE);
        user.setOnDuty(false);
        user.getRoles().add(role);
        return user;
    }

    private Map<String, Object> tokens(AppUser user, String viewTenant) {
        String refresh = Hashes.randomToken();
        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(jwt.hash(refresh));
        stored.setExpiresAt(Instant.now().plus(properties.jwt().refreshTtl()));
        stored.setIpHash(WebRequests.ipHash());
        stored.setUserAgent(WebRequests.userAgent());
        stored.setCreatedAt(Instant.now());
        refreshTokens.saveAndFlush(stored);
        String accessToken = jwt.accessToken(user.getPublicId(), viewTenant, properties.jwt().accessTtl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", accessToken);
        body.put("refreshToken", refresh);
        body.put("expiresInSeconds", properties.jwt().accessTtl().toSeconds());
        body.put("tokenType", "Bearer");
        return body;
    }

    private AppUser requireTenantUser(long tenantId, String publicId) {
        return users.findByPublicId(publicId).filter(user -> user.getTenantId().equals(tenantId))
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private Team requireTeam(long tenantId, String publicId) {
        return teams.findByPublicIdAndTenantId(publicId, tenantId).orElseThrow(() -> ApiException.notFound("Team not found"));
    }

    private Map<String, Object> userView(AppUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getPublicId());
        body.put("name", user.getName());
        body.put("email", user.getEmail());
        body.put("status", user.getStatus());
        body.put("onDuty", user.isOnDuty());
        body.put("roles", users.roleNames(user.getId()));
        return body;
    }

    private Map<String, Object> tenantView(Tenant tenant) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", tenant.getPublicId());
        body.put("name", tenant.getName());
        body.put("status", tenant.getStatus());
        body.put("countryCode", tenant.getCountryCode());
        body.put("timezone", tenant.getTimezone());
        body.put("currencyCode", tenant.getCurrencyCode());
        body.put("locale", tenant.getLocale());
        body.put("assignmentTimeoutMinutes", tenant.getAssignmentTimeoutMinutes());
        body.put("leadRetentionDays", tenant.getLeadRetentionDays());
        return body;
    }

    private Map<String, Object> teamView(Team team) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", team.getPublicId());
        body.put("name", team.getName());
        body.put("status", team.getStatus());
        AppUser manager = team.getManagerUserId() == null ? null : users.findById(team.getManagerUserId()).orElse(null);
        body.put("managerId", manager == null ? null : manager.getPublicId());
        body.put("managerName", manager == null ? null : manager.getName());
        body.put("members", members.findByTeamIdAndTenantIdAndLeftAtIsNull(team.getId(), team.getTenantId()).stream().map(member -> {
            AppUser user = users.findById(member.getUserId()).orElse(null);
            return user == null ? member.getUserId().toString() : user.getPublicId();
        }).toList());
        return body;
    }
}
