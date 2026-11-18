package com.codebuzzers.codebuzzcore.campaign;

import com.codebuzzers.codebuzzcore.billing.EntitlementService;
import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.lead.Lead;
import com.codebuzzers.codebuzzcore.lead.LeadRepository;
import com.codebuzzers.codebuzzcore.lead.LeadSale;
import com.codebuzzers.codebuzzcore.lead.LeadSaleRepository;
import com.codebuzzers.codebuzzcore.meta.MetaAdAccount;
import com.codebuzzers.codebuzzcore.meta.MetaAdAccountRepository;
import com.codebuzzers.codebuzzcore.security.AccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CampaignQueryService {

    private final AccessGuard access;
    private final EntitlementService entitlements;
    private final MetaCampaignRepository campaigns;
    private final CampaignInsightRepository insights;
    private final LeadRepository leads;
    private final LeadSaleRepository sales;
    private final MetaAdSetRepository adSets;
    private final MetaAdAccountRepository adAccounts;

    public CampaignQueryService(AccessGuard access, EntitlementService entitlements, MetaCampaignRepository campaigns,
                                CampaignInsightRepository insights, LeadRepository leads, LeadSaleRepository sales,
                                MetaAdSetRepository adSets, MetaAdAccountRepository adAccounts) {
        this.access = access;
        this.entitlements = entitlements;
        this.campaigns = campaigns;
        this.insights = insights;
        this.leads = leads;
        this.sales = sales;
        this.adSets = adSets;
        this.adAccounts = adAccounts;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        TenantContext.Ctx ctx = require();
        Set<Long> selected = selectedAccountIds(ctx.tenantId());
        return campaigns.findByTenantIdOrderByNameAsc(ctx.tenantId()).stream()
                .filter(campaign -> selected.contains(campaign.getAdAccountId()))
                .map(campaign -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", campaign.getMetaCampaignId());
                    row.put("name", campaign.getName());
                    row.put("status", CampaignSyncService.displayStatus(campaign.getStatus(), campaign.getEndTime()));
                    row.put("objective", campaign.getObjective());
                    row.put("startTime", campaign.getStartTime());
                    row.put("endTime", campaign.getEndTime());
                    return row;
                }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> report(LocalDate from, LocalDate to) {
        TenantContext.Ctx ctx = require();
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Set<String> allowed = selectedCampaignIds(ctx.tenantId());
        Map<String, Mutable> byCampaign = new LinkedHashMap<>();
        for (CampaignInsight insight : insights.campaignsBetween(ctx.tenantId(), from, to)) {
            if (!allowed.contains(insight.getMetaObjectId())) {
                continue;
            }
            Mutable row = byCampaign.computeIfAbsent(insight.getMetaObjectId(), ignored -> new Mutable());
            row.spend = row.spend.add(insight.getSpend());
            row.impressions += insight.getImpressions();
            row.clicks += insight.getClicks();
            row.metaLeads += insight.getLeads();
            row.currency = insight.getCurrencyCode();
        }
        for (Lead lead : leads.findByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ctx.tenantId(), start, end)) {
            if (lead.isTest() || lead.getMetaCampaignId() == null || !allowed.contains(lead.getMetaCampaignId())) {
                continue;
            }
            countLead(byCampaign.computeIfAbsent(lead.getMetaCampaignId(), ignored -> new Mutable()), lead);
        }
        List<Map<String, Object>> rows = byCampaign.entrySet().stream().map(entry -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("campaignId", entry.getKey());
            body.putAll(metrics(entry.getValue(), ctx));
            return body;
        }).toList();
        return Map.of(
                "from", from,
                "to", to,
                "note", "Meta spend and CRM sales stay separate and are joined by campaign id. Ratios are omitted when currencies differ.",
                "rows", rows);
    }

    /** Totals, lead funnel, daily series and ad sets of one campaign for the date range. */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(String metaCampaignId, LocalDate from, LocalDate to) {
        TenantContext.Ctx ctx = require();
        if (from.isAfter(to) || from.plusDays(366).isBefore(to)) {
            throw ApiException.badRequest("invalid_range");
        }
        MetaCampaign campaign = campaigns.findByTenantIdAndMetaCampaignId(ctx.tenantId(), metaCampaignId)
                .orElseThrow(() -> ApiException.notFound("campaign_not_found"));
        if (!selectedAccountIds(ctx.tenantId()).contains(campaign.getAdAccountId())) {
            throw ApiException.notFound("campaign_not_found");
        }
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Mutable total = new Mutable();
        Map<LocalDate, Mutable> perDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            perDay.put(d, new Mutable());
        }
        for (CampaignInsight i : insights.findByTenantIdAndObjectTypeAndMetaObjectIdAndInsightDateBetween(
                ctx.tenantId(), "CAMPAIGN", metaCampaignId, from, to)) {
            addInsight(total, i);
            Mutable day = perDay.get(i.getInsightDate());
            if (day != null) {
                addInsight(day, i);
            }
        }

        List<MetaAdSet> campaignAdSets = adSets.findByTenantIdAndCampaignId(ctx.tenantId(), campaign.getId());
        Map<String, Mutable> perAdSet = new LinkedHashMap<>();
        for (MetaAdSet adSet : campaignAdSets) {
            Mutable row = new Mutable();
            for (CampaignInsight i : insights.findByTenantIdAndObjectTypeAndMetaObjectIdAndInsightDateBetween(
                    ctx.tenantId(), "ADSET", adSet.getMetaAdsetId(), from, to)) {
                addInsight(row, i);
            }
            perAdSet.put(adSet.getMetaAdsetId(), row);
        }

        for (Lead lead : leads.findByTenantIdAndMetaCampaignIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                ctx.tenantId(), metaCampaignId, start, end)) {
            if (lead.isTest() || lead.isSpam()) {
                continue;
            }
            countLead(total, lead);
            Mutable day = perDay.get(lead.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate());
            if (day != null) {
                day.crmLeads++;
            }
            Mutable adSetRow = lead.getMetaAdsetId() == null ? null : perAdSet.get(lead.getMetaAdsetId());
            if (adSetRow != null) {
                countLead(adSetRow, lead);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", campaign.getMetaCampaignId());
        body.put("name", campaign.getName());
        body.put("status", CampaignSyncService.displayStatus(campaign.getStatus(), campaign.getEndTime()));
        body.put("objective", campaign.getObjective());
        body.put("startTime", campaign.getStartTime());
        body.put("endTime", campaign.getEndTime());
        adAccounts.findById(campaign.getAdAccountId()).ifPresent(account -> {
            body.put("adAccountId", account.getMetaAdAccountId());
            body.put("adAccountName", account.getName());
        });
        body.put("from", from);
        body.put("to", to);
        body.put("totals", metrics(total, ctx));
        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("leads", total.crmLeads);
        funnel.put("contacted", total.contacted);
        funnel.put("won", total.won);
        body.put("funnel", funnel);

        List<Map<String, Object>> daily = new ArrayList<>();
        perDay.forEach((date, row) -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", date);
            d.put("spend", row.spend);
            d.put("metaLeads", row.metaLeads);
            d.put("crmLeads", row.crmLeads);
            daily.add(d);
        });
        body.put("daily", daily);

        List<Map<String, Object>> adSetRows = new ArrayList<>();
        for (MetaAdSet adSet : campaignAdSets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", adSet.getMetaAdsetId());
            row.put("name", adSet.getName());
            row.put("status", adSet.getStatus());
            row.putAll(metrics(perAdSet.get(adSet.getMetaAdsetId()), ctx));
            adSetRows.add(row);
        }
        body.put("adSets", adSetRows);
        return body;
    }

    /** Ad accounts the tenant picked in Meta settings; campaigns of any other account are ignored. */
    private Set<Long> selectedAccountIds(long tenantId) {
        return adAccounts.findByTenantIdAndStatus(tenantId, "SELECTED").stream()
                .map(MetaAdAccount::getId).collect(Collectors.toSet());
    }

    private Set<String> selectedCampaignIds(long tenantId) {
        Set<Long> selected = selectedAccountIds(tenantId);
        return campaigns.findByTenantIdOrderByNameAsc(tenantId).stream()
                .filter(c -> selected.contains(c.getAdAccountId()))
                .map(MetaCampaign::getMetaCampaignId).collect(Collectors.toSet());
    }

    private void addInsight(Mutable row, CampaignInsight insight) {
        row.spend = row.spend.add(insight.getSpend());
        row.impressions += insight.getImpressions();
        row.clicks += insight.getClicks();
        row.metaLeads += insight.getLeads();
        row.currency = insight.getCurrencyCode();
    }

    /** One CRM lead into the funnel: lead, contacted, won (with sale value) or lost. */
    private void countLead(Mutable row, Lead lead) {
        row.crmLeads++;
        String status = lead.getStatus();
        if (lead.getFirstResponseAt() != null || Codes.LeadStatus.CONTACTED.equals(status)
                || Codes.LeadStatus.FOLLOW_UP.equals(status) || Codes.LeadStatus.WON.equals(status)
                || Codes.LeadStatus.LOST.equals(status)) {
            row.contacted++;
        }
        if (Codes.LeadStatus.WON.equals(status)) {
            row.won++;
            row.salesMinor += sales.findByLeadId(lead.getId()).map(LeadSale::getAmountMinor).orElse(0L);
        } else if (Codes.LeadStatus.LOST.equals(status)) {
            row.lost++;
        }
    }

    /** The metric block shared by the report rows, campaign totals and ad sets. */
    private Map<String, Object> metrics(Mutable row, TenantContext.Ctx ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("spend", row.spend);
        body.put("currency", row.currency == null ? ctx.currency() : row.currency);
        body.put("impressions", row.impressions);
        body.put("clicks", row.clicks);
        body.put("metaLeads", row.metaLeads);
        body.put("crmLeads", row.crmLeads);
        body.put("contacted", row.contacted);
        body.put("won", row.won);
        body.put("lost", row.lost);
        body.put("salesMinor", row.salesMinor);
        // cost per lead uses the leads Meta reports, falling back to CRM leads
        long leadBase = row.metaLeads > 0 ? row.metaLeads : row.crmLeads;
        body.put("cpl", leadBase == 0 ? null : row.spend.divide(BigDecimal.valueOf(leadBase), 4, RoundingMode.HALF_UP));
        body.put("costPerSale", row.won == 0 ? null : row.spend.divide(BigDecimal.valueOf(row.won), 4, RoundingMode.HALF_UP));
        boolean sameCurrency = row.currency == null || row.currency.equals(ctx.currency());
        body.put("saleValueOverSpend", !sameCurrency || row.spend.signum() == 0 ? null
                : BigDecimal.valueOf(row.salesMinor).movePointLeft(2).divide(row.spend, 4, RoundingMode.HALF_UP));
        return body;
    }

    private TenantContext.Ctx require() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.CAMPAIGN_VIEW);
        entitlements.requireFeature(ctx.tenantId(), Codes.Feature.CAMPAIGN_ANALYTICS);
        return ctx;
    }

    private static final class Mutable {
        private BigDecimal spend = BigDecimal.ZERO;
        private long impressions;
        private long clicks;
        private long metaLeads;
        private long crmLeads;
        private long contacted;
        private long won;
        private long lost;
        private long salesMinor;
        private String currency;
    }
}