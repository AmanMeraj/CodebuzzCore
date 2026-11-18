package com.codebuzzers.codebuzzcore.billing;

import com.codebuzzers.codebuzzcore.audit.AuditService;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.security.AccessGuard;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final AccessGuard access;
    private final EntitlementService entitlements;
    private final AuditService audit;

    public SubscriptionController(AccessGuard access, EntitlementService entitlements, AuditService audit) {
        this.access = access;
        this.entitlements = entitlements;
        this.audit = audit;
    }

    @GetMapping
    public Map<String, Object> current() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.BILLING_VIEW);
        Plan plan = entitlements.currentPlan(ctx.tenantId());
        TenantLimit limits = entitlements.currentLimits(ctx.tenantId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", plan.getCode());
        body.put("name", plan.getName());
        body.put("features", entitlements.featureCodes(ctx.tenantId()));
        body.put("maxUsers", limits.getMaxUsers());
        body.put("maxAdAccounts", limits.getMaxAdAccounts());
        body.put("maxLeadsPerMonth", limits.getMaxLeadsPerMonth());
        body.put("leadsUsedThisPeriod", entitlements.leadsUsed(ctx.tenantId(), ctx.timezone()));
        body.put("period", entitlements.currentPeriod(ctx.timezone()));
        return body;
    }

    @PostMapping("/plan")
    @Transactional
    public Map<String, Object> change(@RequestBody PlanChange request) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.USER_MANAGE);
        entitlements.changePlan(ctx.tenantId(), request.code(), ctx.timezone());
        audit.record(ctx.tenantId(), ctx.userId(), "PLAN_CHANGED", "SUBSCRIPTION", request.code(), null, null, Codes.Source.ANDROID);
        return current();
    }

    public record PlanChange(String code) {
    }
}
