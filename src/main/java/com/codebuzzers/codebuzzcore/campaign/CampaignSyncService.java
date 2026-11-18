package com.codebuzzers.codebuzzcore.campaign;

import com.codebuzzers.codebuzzcore.common.CryptoService;
import com.codebuzzers.codebuzzcore.common.Jsons;
import com.codebuzzers.codebuzzcore.job.PermanentFailure;
import com.codebuzzers.codebuzzcore.lead.LeadIngestionService;
import com.codebuzzers.codebuzzcore.job.TransientFailure;
import com.codebuzzers.codebuzzcore.meta.MetaAdAccount;
import com.codebuzzers.codebuzzcore.meta.MetaAdAccountRepository;
import com.codebuzzers.codebuzzcore.meta.MetaApiException;
import com.codebuzzers.codebuzzcore.meta.MetaConnection;
import com.codebuzzers.codebuzzcore.meta.MetaConnectionRepository;
import com.codebuzzers.codebuzzcore.meta.MetaGraphClient;
import com.codebuzzers.codebuzzcore.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class CampaignSyncService {

    private static final Logger log = LoggerFactory.getLogger(CampaignSyncService.class);
    private static final int INSIGHT_DAYS = 30;
    private static final int FIRST_SYNC_INSIGHT_DAYS = 90;
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final MetaConnectionRepository connections;
    private final MetaAdAccountRepository adAccounts;
    private final MetaCampaignRepository campaigns;
    private final MetaAdSetRepository adSets;
    private final MetaAdRepository ads;
    private final CampaignInsightRepository insights;
    private final MetaGraphClient graph;
    private final CryptoService crypto;
    private final NotificationService notifications;
    private final LeadIngestionService leadIngestion;

    public CampaignSyncService(MetaConnectionRepository connections, MetaAdAccountRepository adAccounts,
                               MetaCampaignRepository campaigns, MetaAdSetRepository adSets, MetaAdRepository ads,
                               CampaignInsightRepository insights, MetaGraphClient graph, CryptoService crypto,
                               NotificationService notifications, LeadIngestionService leadIngestion) {
        this.connections = connections;
        this.adAccounts = adAccounts;
        this.campaigns = campaigns;
        this.adSets = adSets;
        this.ads = ads;
        this.insights = insights;
        this.graph = graph;
        this.crypto = crypto;
        this.notifications = notifications;
        this.leadIngestion = leadIngestion;
    }

    public void syncConnection(long connectionId) {
        MetaConnection connection = connections.findById(connectionId).orElseThrow(() -> new PermanentFailure("connection_missing"));
        if (!"ACTIVE".equals(connection.getStatus())) {
            return;
        }
        String token = crypto.decrypt(connection.getEncryptedAccessToken());
        if (token == null || token.isBlank()) {
            throw new PermanentFailure("missing_token");
        }
        // Leads first and on their own: they use page tokens, so a rate limit or error in the
        // ad account calls below must not stop them, and they must not stop the spend sync.
        try {
            leadIngestion.backfill(connection);
        } catch (Exception ex) {
            log.warn("Lead backfill failed: {}", ex.getMessage());
        }
        try {
            for (MetaAdAccount account : adAccounts.findByTenantIdAndStatus(connection.getTenantId(), "SELECTED")) {
                if (!account.getConnectionId().equals(connection.getId())) {
                    continue;
                }
                syncAccount(connection, account, token);
            }
        } catch (MetaApiException ex) {
            if (ex.tokenInvalid()) {
                connection.setStatus("UNHEALTHY");
                connection.setHealthMessage(ex.getMessage());
                connection.setUpdatedAt(Instant.now());
                connections.save(connection);
                throw new PermanentFailure(ex.getMessage());
            }
            if (ex.transientFailure()) {
                throw new TransientFailure(ex.getMessage());
            }
            throw new PermanentFailure(ex.getMessage());
        }
    }

    private void syncAccount(MetaConnection connection, MetaAdAccount account, String token) {
        // Insights first: spend and leads must never depend on the ad set / ad calls below.
        try {
            syncInsights(connection.getTenantId(), account, token);
        } catch (MetaApiException ex) {
            if (ex.tokenInvalid() || ex.rateLimited()) {
                throw ex; // rate limited: stop now and let the job retry later instead of hammering Meta
            }
            log.warn("Insight sync failed for account {}: {}", account.getMetaAdAccountId(), ex.getMessage());
        }

        Map<String, MetaCampaign> byMetaId = new java.util.LinkedHashMap<>();
        String after = null;
        for (int page = 0; page < 20; page++) {
            Map<String, String> query = new java.util.LinkedHashMap<>();
            query.put("fields", "id,name,status,effective_status,objective,start_time,stop_time");
            query.put("limit", "100");
            if (after != null) {
                query.put("after", after);
            }
            JsonNode campaignPage = graph.get(account.getMetaAdAccountId() + "/campaigns", token, query);
            for (JsonNode node : campaignPage.path("data")) {
                MetaCampaign saved = saveCampaign(connection.getTenantId(), account, node);
                if (saved != null) {
                    byMetaId.put(saved.getMetaCampaignId(), saved);
                }
            }
            after = nextCursor(campaignPage);
            if (after == null) {
                break;
            }
        }

        try {
            syncAdSets(connection.getTenantId(), account, token, byMetaId);
        } catch (MetaApiException ex) {
            if (ex.tokenInvalid() || ex.rateLimited()) {
                throw ex;
            }
            log.warn("Ad set/ad sync failed for account {}: {}", account.getMetaAdAccountId(), ex.getMessage());
        }
    }

    private MetaCampaign saveCampaign(long tenantId, MetaAdAccount account, JsonNode node) {
        String metaId = Jsons.text(node.path("id"));
        if (metaId == null) {
            return null;
        }
        MetaCampaign campaign = campaigns.findByTenantIdAndMetaCampaignId(tenantId, metaId).orElseGet(MetaCampaign::new);
        campaign.setTenantId(tenantId);
        campaign.setAdAccountId(account.getId());
        campaign.setMetaCampaignId(metaId);
        campaign.setName(textOr(node, "name", "Campaign"));
        campaign.setObjective(Jsons.text(node.path("objective")));
        campaign.setStartTime(parseTime(Jsons.text(node.path("start_time"))));
        campaign.setEndTime(parseTime(Jsons.text(node.path("stop_time"))));
        String metaStatus = Jsons.text(node.path("effective_status"));
        if (metaStatus == null) {
            metaStatus = Jsons.text(node.path("status"));
        }
        campaign.setStatus(displayStatus(metaStatus, campaign.getEndTime()));
        return campaigns.save(campaign);
    }

    /** Pulls spend per day for every campaign and every ad set. Last 30 days normally, 90 on a tenant's first sync. */
    private void syncInsights(long tenantId, MetaAdAccount account, String token) {
        int days = insights.existsByTenantId(tenantId) ? INSIGHT_DAYS : FIRST_SYNC_INSIGHT_DAYS;
        LocalDate until = LocalDate.now(ZoneOffset.UTC);
        LocalDate since = until.minusDays(days);
        pullInsights(tenantId, account, token, since, until, "campaign", "CAMPAIGN", "campaign_id");
        try {
            pullInsights(tenantId, account, token, since, until, "adset", "ADSET", "adset_id");
        } catch (MetaApiException ex) {
            if (ex.tokenInvalid()) {
                throw ex;
            }
            log.warn("Ad set insight sync failed for account {}: {}", account.getMetaAdAccountId(), ex.getMessage());
        }
    }

    private void pullInsights(long tenantId, MetaAdAccount account, String token, LocalDate since, LocalDate until,
                              String level, String objectType, String idField) {
        String after = null;
        for (int page = 0; page < 50; page++) {
            Map<String, String> query = new java.util.LinkedHashMap<>();
            query.put("level", level);
            query.put("fields", idField + ",spend,impressions,clicks,actions,date_start");
            query.put("time_range", "{\"since\":\"" + since + "\",\"until\":\"" + until + "\"}");
            query.put("time_increment", "1");
            query.put("limit", "200");
            if (after != null) {
                query.put("after", after);
            }
            JsonNode insightPage = graph.get(account.getMetaAdAccountId() + "/insights", token, query);
            for (JsonNode row : insightPage.path("data")) {
                upsertInsight(tenantId, account.getCurrencyCode(), objectType, idField, row);
            }
            after = nextCursor(insightPage);
            if (after == null) {
                break;
            }
        }
    }

    /** Graph paging: the cursor for the next page, or null on the last page. */
    private static String nextCursor(JsonNode page) {
        if (page.path("paging").path("next").isMissingNode() || page.path("paging").path("next").isNull()) {
            return null;
        }
        return Jsons.text(page.path("paging").path("cursors").path("after"));
    }

    /** ACTIVE / PAUSED / COMPLETED (stop time passed) / otherwise Meta's own status. */
    static String displayStatus(String metaStatus, Instant end) {
        if (metaStatus == null) {
            return null;
        }
        boolean removed = "ARCHIVED".equals(metaStatus) || "DELETED".equals(metaStatus);
        if (!removed && end != null && end.isBefore(Instant.now())) {
            return "COMPLETED";
        }
        if (metaStatus.contains("PAUSED")) {
            return "PAUSED";
        }
        return metaStatus;
    }

    /**
     * Ad sets and ads are read per ad account (a few paged calls) instead of per campaign, which
     * used to be hundreds of calls and tripped Meta's rate limit (error 17).
     * A campaign with no stop time whose ad sets have all ended is marked completed.
     */
    private void syncAdSets(long tenantId, MetaAdAccount account, String token, Map<String, MetaCampaign> byMetaId) {
        Map<String, Instant> latestEnd = new java.util.HashMap<>();
        java.util.Set<String> hasOpenAdSet = new java.util.HashSet<>();
        Map<String, Long> adSetLocalIds = new java.util.HashMap<>();
        Instant now = Instant.now();
        String after = null;
        for (int page = 0; page < 50; page++) {
            Map<String, String> query = new java.util.LinkedHashMap<>();
            query.put("fields", "id,name,status,end_time,campaign_id");
            query.put("limit", "200");
            if (after != null) {
                query.put("after", after);
            }
            JsonNode adsetPage = graph.get(account.getMetaAdAccountId() + "/adsets", token, query);
            for (JsonNode node : adsetPage.path("data")) {
                String metaId = Jsons.text(node.path("id"));
                String campaignMetaId = Jsons.text(node.path("campaign_id"));
                MetaCampaign campaign = campaignMetaId == null ? null : byMetaId.get(campaignMetaId);
                if (metaId == null || campaign == null) {
                    continue;
                }
                Instant end = parseTime(Jsons.text(node.path("end_time")));
                if (end == null || end.isAfter(now)) {
                    hasOpenAdSet.add(campaignMetaId);
                } else {
                    latestEnd.merge(campaignMetaId, end, (x, y) -> x.isAfter(y) ? x : y);
                }
                MetaAdSet adSet = adSets.findByTenantIdAndMetaAdsetId(tenantId, metaId).orElseGet(MetaAdSet::new);
                adSet.setTenantId(tenantId);
                adSet.setCampaignId(campaign.getId());
                adSet.setMetaAdsetId(metaId);
                adSet.setName(textOr(node, "name", "Ad set"));
                adSet.setStatus(Jsons.text(node.path("status")));
                adSets.save(adSet);
                adSetLocalIds.put(metaId, adSet.getId());
            }
            after = nextCursor(adsetPage);
            if (after == null) {
                break;
            }
        }
        for (Map.Entry<String, Instant> entry : latestEnd.entrySet()) {
            MetaCampaign campaign = byMetaId.get(entry.getKey());
            if (campaign == null || hasOpenAdSet.contains(entry.getKey())) {
                continue;
            }
            if (campaign.getEndTime() == null) {
                campaign.setEndTime(entry.getValue());
            }
            campaign.setStatus(displayStatus(campaign.getStatus(), campaign.getEndTime()));
            campaigns.save(campaign);
        }

        after = null;
        for (int page = 0; page < 20; page++) {
            Map<String, String> query = new java.util.LinkedHashMap<>();
            query.put("fields", "id,name,status,adset_id");
            query.put("limit", "200");
            if (after != null) {
                query.put("after", after);
            }
            JsonNode adPage = graph.get(account.getMetaAdAccountId() + "/ads", token, query);
            for (JsonNode adNode : adPage.path("data")) {
                String adId = Jsons.text(adNode.path("id"));
                Long adSetId = adSetLocalIds.get(Jsons.text(adNode.path("adset_id")));
                if (adId == null || adSetId == null) {
                    continue;
                }
                MetaAd ad = ads.findByTenantIdAndMetaAdId(tenantId, adId).orElseGet(MetaAd::new);
                ad.setTenantId(tenantId);
                ad.setAdSetId(adSetId);
                ad.setMetaAdId(adId);
                ad.setName(textOr(adNode, "name", "Ad"));
                ad.setStatus(Jsons.text(adNode.path("status")));
                ads.save(ad);
            }
            after = nextCursor(adPage);
            if (after == null) {
                break;
            }
        }
    }

    @Transactional
    public void upsertInsight(long tenantId, String currency, String objectType, String idField, JsonNode row) {
        String campaignId = Jsons.text(row.path(idField));
        String date = Jsons.text(row.path("date_start"));
        if (campaignId == null || date == null) {
            return;
        }
        LocalDate day = LocalDate.parse(date);
        CampaignInsight insight = insights.findByTenantIdAndObjectTypeAndMetaObjectIdAndInsightDate(tenantId, objectType, campaignId, day)
                .orElseGet(CampaignInsight::new);
        insight.setTenantId(tenantId);
        insight.setObjectType(objectType);
        insight.setMetaObjectId(campaignId);
        insight.setInsightDate(day);
        insight.setSpend(decimal(Jsons.text(row.path("spend"))));
        insight.setCurrencyCode(currency == null ? "USD" : currency);
        insight.setImpressions(longValue(Jsons.text(row.path("impressions"))));
        insight.setClicks(longValue(Jsons.text(row.path("clicks"))));
        insight.setLeads(leadActions(row.path("actions")));
        insights.save(insight);
    }

    /**
     * Meta reports the same lead under several action types (lead and onsite_conversion.lead_grouped),
     * so summing them double counts. Use "lead", then the grouped type, then any other lead type.
     */
    private static long leadActions(JsonNode actions) {
        long lead = 0;
        long grouped = 0;
        long other = 0;
        for (JsonNode action : actions) {
            String type = Jsons.text(action.path("action_type"));
            if (type == null) {
                continue;
            }
            long value = longValue(Jsons.text(action.path("value")));
            if ("lead".equals(type)) {
                lead += value;
            } else if ("onsite_conversion.lead_grouped".equals(type)) {
                grouped += value;
            } else if (type.toLowerCase().contains("lead")) {
                other += value;
            }
        }
        return lead > 0 ? lead : (grouped > 0 ? grouped : other);
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null ? BigDecimal.ZERO : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static long longValue(String value) {
        try {
            return value == null ? 0 : new BigDecimal(value).longValue();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String value = Jsons.text(node.path(field));
        return value == null ? fallback : value;
    }

    private static Instant parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, GRAPH_TIME).toInstant();
        } catch (Exception ex) {
            return null;
        }
    }
}