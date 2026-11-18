package com.codebuzzers.codebuzzcore.billing;

import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.Codes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
public class EntitlementService {

    private final TenantSubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PlanFeatureRepository features;
    private final TenantLimitRepository limits;
    private final UsageRecordRepository usage;

    public EntitlementService(TenantSubscriptionRepository subscriptions, PlanRepository plans,
                              PlanFeatureRepository features, TenantLimitRepository limits,
                              UsageRecordRepository usage) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.features = features;
        this.limits = limits;
        this.usage = usage;
    }

    public Plan requirePlan(String code) {
        return plans.findByCode(code).filter(Plan::isActive)
                .orElseThrow(() -> ApiException.badRequest("Unknown plan"));
    }

    public void subscribe(long tenantId, Plan plan, String timezone) {
        Period period = currentPeriod(timezone);
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanId(plan.getId());
        subscription.setStatus("ACTIVE");
        subscription.setCurrentPeriodStart(period.start());
        subscription.setCurrentPeriodEnd(period.end());
        subscriptions.save(subscription);
        copyLimits(tenantId, plan);
    }

    @Transactional
    public void changePlan(long tenantId, String planCode, String timezone) {
        Plan plan = requirePlan(planCode);
        TenantSubscription subscription = subscriptions.findByTenantId(tenantId)
                .orElseThrow(() -> ApiException.notFound("Subscription not found"));
        subscription.setPlanId(plan.getId());
        subscription.setStatus("ACTIVE");
        Period period = currentPeriod(timezone);
        subscription.setCurrentPeriodStart(period.start());
        subscription.setCurrentPeriodEnd(period.end());
        copyLimits(tenantId, plan);
    }

    public boolean enabled(long tenantId, String featureCode) {
        TenantSubscription subscription = subscriptions.findByTenantId(tenantId).orElse(null);
        if (subscription == null || !"ACTIVE".equals(subscription.getStatus())) {
            return false;
        }
        return features.findByPlanIdAndEnabledTrue(subscription.getPlanId()).stream()
                .anyMatch(feature -> feature.getFeatureCode().equals(featureCode));
    }

    public void requireFeature(long tenantId, String featureCode) {
        if (!enabled(tenantId, featureCode)) {
            throw ApiException.forbidden("The current package does not include " + featureCode);
        }
    }

    public List<String> featureCodes(long tenantId) {
        TenantSubscription subscription = subscriptions.findByTenantId(tenantId).orElse(null);
        if (subscription == null) {
            return List.of();
        }
        return features.findByPlanIdAndEnabledTrue(subscription.getPlanId()).stream()
                .map(PlanFeature::getFeatureCode)
                .sorted()
                .toList();
    }

    public Plan currentPlan(long tenantId) {
        TenantSubscription subscription = subscriptions.findByTenantId(tenantId)
                .orElseThrow(() -> ApiException.notFound("Subscription not found"));
        return plans.findById(subscription.getPlanId()).orElseThrow(() -> ApiException.notFound("Plan not found"));
    }

    public TenantLimit currentLimits(long tenantId) {
        return limits.findByTenantId(tenantId).orElseThrow(() -> ApiException.notFound("Limits not found"));
    }

    public void requireUserSlot(long tenantId, long activeUsers) {
        TenantLimit limit = currentLimits(tenantId);
        if (activeUsers >= limit.getMaxUsers()) {
            throw ApiException.forbidden("The user limit for this package has been reached");
        }
    }

    public void requireAdAccountSlot(long tenantId, long selectedAccounts) {
        TenantLimit limit = currentLimits(tenantId);
        if (selectedAccounts > limit.getMaxAdAccounts()) {
            throw ApiException.forbidden("The ad account limit for this package has been reached");
        }
    }

    @Transactional
    public long incrementLeads(long tenantId, String timezone) {
        Period period = currentPeriod(timezone);
        usage.increment(tenantId, period.start(), period.end(), "LEADS");
        return usage.findByTenantIdAndPeriodStartAndMetric(tenantId, period.start(), "LEADS")
                .map(UsageRecord::getQuantity)
                .orElse(1L);
    }

    public long leadsUsed(long tenantId, String timezone) {
        Period period = currentPeriod(timezone);
        return usage.findByTenantIdAndPeriodStartAndMetric(tenantId, period.start(), "LEADS")
                .map(UsageRecord::getQuantity)
                .orElse(0L);
    }

    public Period currentPeriod(String timezone) {
        ZoneId zone = zone(timezone);
        YearMonth month = YearMonth.now(zone);
        return new Period(month.atDay(1), month.atEndOfMonth());
    }

    public static ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }

    private void copyLimits(long tenantId, Plan plan) {
        TenantLimit limit = limits.findByTenantId(tenantId).orElseGet(TenantLimit::new);
        limit.setTenantId(tenantId);
        limit.setMaxUsers(plan.getMaxUsers());
        limit.setMaxAdAccounts(plan.getMaxAdAccounts());
        limit.setMaxLeadsPerMonth(plan.getMaxLeadsPerMonth());
        limit.setUpdatedAt(Instant.now());
        limits.save(limit);
    }

    public record Period(LocalDate start, LocalDate end) {
    }
}
