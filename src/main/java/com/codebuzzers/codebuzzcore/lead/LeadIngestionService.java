package com.codebuzzers.codebuzzcore.lead;

import com.codebuzzers.codebuzzcore.billing.EntitlementService;
import com.codebuzzers.codebuzzcore.campaign.MetaCampaignRepository;
import com.codebuzzers.codebuzzcore.common.AppProperties;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.CryptoService;
import com.codebuzzers.codebuzzcore.common.Jsons;
import com.codebuzzers.codebuzzcore.identity.AppUserRepository;
import com.codebuzzers.codebuzzcore.identity.Tenant;
import com.codebuzzers.codebuzzcore.identity.TenantRepository;
import com.codebuzzers.codebuzzcore.job.PermanentFailure;
import com.codebuzzers.codebuzzcore.job.TransientFailure;
import com.codebuzzers.codebuzzcore.meta.MetaAdAccountRepository;
import com.codebuzzers.codebuzzcore.meta.MetaApiException;
import com.codebuzzers.codebuzzcore.meta.MetaConnection;
import com.codebuzzers.codebuzzcore.meta.MetaConnectionRepository;
import com.codebuzzers.codebuzzcore.meta.MetaGraphClient;
import com.codebuzzers.codebuzzcore.meta.MetaPage;
import com.codebuzzers.codebuzzcore.meta.MetaPageRepository;
import com.codebuzzers.codebuzzcore.notification.NotificationService;
import com.codebuzzers.codebuzzcore.webhook.WebhookAttempt;
import com.codebuzzers.codebuzzcore.webhook.WebhookAttemptRepository;
import com.codebuzzers.codebuzzcore.webhook.WebhookEvent;
import com.codebuzzers.codebuzzcore.webhook.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class LeadIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LeadIngestionService.class);
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final WebhookEventRepository events;
    private final WebhookAttemptRepository attempts;
    private final MetaPageRepository pages;
    private final MetaConnectionRepository connections;
    private final MetaAdAccountRepository adAccounts;
    private final MetaGraphClient graph;
    private final CryptoService crypto;
    private final LeadRepository leads;
    private final LeadStatusHistoryRepository history;
    private final TenantRepository tenants;
    private final AssignmentService assignmentService;
    private final EntitlementService entitlements;
    private final NotificationService notifications;
    private final AppUserRepository users;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<LeadIngestionService> self;
    private final AppProperties properties;
    private final MetaCampaignRepository campaignRepo;
    /** Pages already pulled with the full 90 day window in this run of the server. */
    private final java.util.Set<String> fullyBackfilledPages = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public LeadIngestionService(WebhookEventRepository events, WebhookAttemptRepository attempts,
                                MetaPageRepository pages, MetaConnectionRepository connections,
                                MetaAdAccountRepository adAccounts, MetaGraphClient graph, CryptoService crypto,
                                LeadRepository leads, LeadStatusHistoryRepository history, TenantRepository tenants,
                                AssignmentService assignmentService, EntitlementService entitlements,
                                NotificationService notifications, AppUserRepository users, ObjectMapper objectMapper,
                                ObjectProvider<LeadIngestionService> self, AppProperties properties,
                                MetaCampaignRepository campaignRepo) {
        this.events = events;
        this.attempts = attempts;
        this.pages = pages;
        this.connections = connections;
        this.adAccounts = adAccounts;
        this.graph = graph;
        this.crypto = crypto;
        this.leads = leads;
        this.history = history;
        this.tenants = tenants;
        this.assignmentService = assignmentService;
        this.entitlements = entitlements;
        this.notifications = notifications;
        this.users = users;
        this.objectMapper = objectMapper;
        this.self = self;
        this.properties = properties;
        this.campaignRepo = campaignRepo;
    }

    public void ingest(long webhookEventId) {
        WebhookEvent event = events.findById(webhookEventId).orElseThrow(() -> new PermanentFailure("webhook_missing"));
        if (Codes.WebhookStatus.DONE.equals(event.getStatus()) || Codes.WebhookStatus.DEAD.equals(event.getStatus())) {
            return;
        }
        JsonNode payload = objectMapper.readTree(event.getPayloadJson());
        String leadgenId = null;
        String pageId = null;
        String formId = null;
        String adId = null;
        String adsetId = null;
        for (JsonNode entry : payload.path("entry")) {
            pageId = firstNonBlank(pageId, Jsons.text(entry.path("id")));
            for (JsonNode change : entry.path("changes")) {
                if (!"leadgen".equals(Jsons.text(change.path("field")))) {
                    continue;
                }
                JsonNode value = change.path("value");
                leadgenId = firstNonBlank(leadgenId, Jsons.text(value.path("leadgen_id")));
                pageId = firstNonBlank(pageId, Jsons.text(value.path("page_id")));
                formId = firstNonBlank(formId, Jsons.text(value.path("form_id")));
                adId = firstNonBlank(adId, Jsons.text(value.path("ad_id")));
                adsetId = firstNonBlank(adsetId, Jsons.text(value.path("adgroup_id")));
            }
        }
        if (leadgenId == null || pageId == null) {
            self.getObject().finish(event, null, Codes.WebhookStatus.DEAD, "malformed_payload");
            throw new PermanentFailure("malformed_payload");
        }
        List<MetaPage> matches = pages.findByMetaPageIdAndStatus(pageId, "SELECTED");
        if (matches.isEmpty()) {
            recordAttempt(event, "page_not_connected");
            throw new TransientFailure("page_not_connected");
        }
        if (matches.size() > 1) {
            self.getObject().finish(event, null, Codes.WebhookStatus.DEAD, "ambiguous_page");
            throw new PermanentFailure("ambiguous_page");
        }
        MetaPage page = matches.get(0);
        MetaConnection connection = connections.findById(page.getConnectionId()).orElse(null);
        if (connection == null || !"ACTIVE".equals(connection.getStatus())) {
            self.getObject().finish(event, page.getTenantId(), Codes.WebhookStatus.DEAD, "connection_unhealthy");
            throw new PermanentFailure("connection_unhealthy");
        }
        String token = crypto.decrypt(page.getEncryptedPageToken());
        if (token == null || token.isBlank()) {
            token = crypto.decrypt(connection.getEncryptedAccessToken());
        }
        if (token == null || token.isBlank()) {
            self.getObject().finish(event, page.getTenantId(), Codes.WebhookStatus.DEAD, "missing_token");
            throw new PermanentFailure("missing_token");
        }
        JsonNode leadJson;
        try {
            leadJson = graph.get(leadgenId, token, Map.of(
                    "fields", "id,created_time,ad_id,form_id,field_data,campaign_id,adset_id,is_test"));
        } catch (MetaApiException ex) {
            if (ex.tokenInvalid()) {
                markUnhealthy(connection, ex.getMessage());
                self.getObject().finish(event, page.getTenantId(), Codes.WebhookStatus.DEAD, ex.getMessage());
                throw new PermanentFailure(ex.getMessage());
            }
            recordAttempt(event, ex.getMessage());
            if (ex.transientFailure()) {
                throw new TransientFailure(ex.getMessage());
            }
            self.getObject().finish(event, page.getTenantId(), Codes.WebhookStatus.DEAD, ex.getMessage());
            throw new PermanentFailure(ex.getMessage());
        }
        self.getObject().store(page, connection, leadJson, pageId, formId, adId, adsetId);
        self.getObject().finish(event, page.getTenantId(), Codes.WebhookStatus.DONE, null);
    }

    @Transactional
    public void store(MetaPage page, MetaConnection connection, JsonNode leadJson, String pageId,
                      String formId, String adId, String adsetId) {
        store(page, connection, leadJson, pageId, formId, adId, adsetId, false);
    }

    /**
     * Backfilled leads (pulled from Meta, not delivered by the webhook) are stored as NEW and unassigned:
     * no quota usage, no notifications and no round-robin assignment for old leads.
     */
    @Transactional
    public void store(MetaPage page, MetaConnection connection, JsonNode leadJson, String pageId,
                      String formId, String adId, String adsetId, boolean backfill) {
        String metaLeadId = Jsons.text(leadJson.path("id"));
        if (metaLeadId == null) {
            throw new PermanentFailure("malformed_lead");
        }
        if (leads.findByTenantIdAndMetaLeadId(page.getTenantId(), metaLeadId).isPresent()) {
            return;
        }
        if (backfill && LeadNormalizer.answers(leadJson).values().stream().allMatch(List::isEmpty)) {
            return; // Meta only returns a lead's answers for 90 days; older leads come back empty and are useless
        }
        Tenant tenant = tenants.findById(page.getTenantId()).orElseThrow(() -> new PermanentFailure("tenant_missing"));
        LeadNormalizer.Result normalized = LeadNormalizer.normalize(leadJson, tenant.getCountryCode());
        if (normalized.name() == null && normalized.phone() == null && normalized.email() == null) {
            log.warn("Lead {} has answers but no recognizable name/phone/email; question keys={}",
                    metaLeadId, LeadNormalizer.answers(leadJson).keySet());
        }
        boolean duplicate = normalized.phone() != null
                && leads.existsByTenantIdAndPhoneNormalizedAndMetaLeadIdNot(page.getTenantId(), normalized.phone(), metaLeadId);
        Lead lead = new Lead();
        lead.setTenantId(page.getTenantId());
        lead.setMetaLeadId(metaLeadId);
        lead.setMetaPageId(pageId);
        lead.setMetaFormId(firstNonBlank(formId, Jsons.text(leadJson.path("form_id"))));
        lead.setMetaCampaignId(Jsons.text(leadJson.path("campaign_id")));
        lead.setMetaAdsetId(firstNonBlank(Jsons.text(leadJson.path("adset_id")), adsetId));
        lead.setMetaAdId(firstNonBlank(Jsons.text(leadJson.path("ad_id")), adId));
        // The lead's ad account is the one its campaign belongs to; never guessed from the current selection.
        if (lead.getMetaCampaignId() != null) {
            campaignRepo.findByTenantIdAndMetaCampaignId(page.getTenantId(), lead.getMetaCampaignId())
                    .flatMap(campaign -> adAccounts.findById(campaign.getAdAccountId()))
                    .ifPresent(account -> lead.setMetaAdAccountId(account.getMetaAdAccountId()));
        }
        lead.setName(normalized.name());
        lead.setPhoneNormalized(normalized.phone());
        lead.setEmailNormalized(normalized.email());
        lead.setTest(normalized.test());
        lead.setSpam(normalized.spam());
        if (normalized.test()) {
            lead.setStatus(Codes.LeadStatus.NEW);
        } else if (normalized.spam()) {
            lead.setStatus(Codes.LeadStatus.SPAM);
        } else if (duplicate) {
            lead.setStatus(Codes.LeadStatus.DUPLICATE);
        } else {
            lead.setStatus(Codes.LeadStatus.NEW);
        }
        lead.setRawPayload(leadJson.toString());
        lead.setMetaCreatedAt(parseTime(Jsons.text(leadJson.path("created_time"))));
        try {
            leads.saveAndFlush(lead);
        } catch (DataIntegrityViolationException ex) {
            return;
        }
        LeadStatusHistory status = new LeadStatusHistory();
        status.setTenantId(lead.getTenantId());
        status.setLeadId(lead.getId());
        status.setToStatus(lead.getStatus());
        status.setChangedAt(Instant.now());
        history.save(status);
        if (backfill) {
            return;
        }
        if (!lead.isTest()) {
            long used = entitlements.incrementLeads(lead.getTenantId(), tenant.getTimezone());
            int limit = entitlements.currentLimits(lead.getTenantId()).getMaxLeadsPerMonth();
            if (used > limit) {
                lead.setOverQuota(true);
                notifyOwners(lead, tenant, "Lead quota exceeded",
                        "A lead was stored after the monthly limit. It is marked over quota.",
                        "quota:" + entitlements.currentPeriod(tenant.getTimezone()).start());
            }
        }
        if (!entitlements.enabled(lead.getTenantId(), Codes.Feature.LEAD_INGESTION)) {
            notifyOwners(lead, tenant, "Lead ingestion is disabled",
                    "A Meta lead was stored but not assigned because the package disables ingestion.",
                    "ingestion-disabled");
            return;
        }
        assignmentService.assignNew(lead);
    }

    /**
     * Pulls leads straight from Meta (page lead forms) for pages that were already connected, so leads
     * that arrived while the webhook was not reachable still show up. Existing leads are skipped.
     * Full history on a tenant's first run, then only the last 3 days.
     */
    public void backfill(MetaConnection connection) {
        long tenantId = connection.getTenantId();
        try {
            self.getObject().repairUnparsedLeads(tenantId);
        } catch (RuntimeException ex) {
            log.warn("Lead repair failed: {} {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
        List<MetaPage> selectedPages = pages.findByTenantIdAndStatus(tenantId, "SELECTED");
        log.info("Lead backfill: tenant {} has {} selected page(s)", tenantId, selectedPages.size());
        for (MetaPage page : selectedPages) {
            if (!page.getConnectionId().equals(connection.getId())) {
                log.info("Lead backfill: page {} belongs to another connection, skipped", page.getMetaPageId());
                continue;
            }
            String token = crypto.decrypt(page.getEncryptedPageToken());
            if (token == null || token.isBlank()) {
                token = crypto.decrypt(connection.getEncryptedAccessToken());
            }
            if (token == null || token.isBlank()) {
                log.warn("Lead backfill: no token for page {}", page.getMetaPageId());
                continue;
            }
            // First time this server run sees a page: the full 90 days Meta keeps. After that: last 3 days.
            boolean full = fullyBackfilledPages.add(page.getMetaPageId());
            String since = String.valueOf(Instant.now().minus(full ? 90 : 3, ChronoUnit.DAYS).getEpochSecond());
            log.info("Lead backfill: page {} window = last {} days", page.getMetaPageId(), full ? 90 : 3);
            try {
                backfillPage(page, connection, token, since);
            } catch (MetaApiException ex) {
                if (full) {
                    fullyBackfilledPages.remove(page.getMetaPageId()); // retry the full window next time
                }
                log.warn("Lead backfill failed for page {}: {} {}", page.getMetaPageId(), ex.getMessage(), ex.detail());
                if (ex.rateLimited()) {
                    return;
                }
            }
        }
    }

    private void backfillPage(MetaPage page, MetaConnection connection, String token, String since) {
        String formAfter = null;
        for (int formPage = 0; formPage < 5; formPage++) {
            Map<String, String> formQuery = new java.util.LinkedHashMap<>();
            formQuery.put("fields", "id,name,status");
            formQuery.put("limit", "100");
            if (formAfter != null) {
                formQuery.put("after", formAfter);
            }
            JsonNode forms = graph.get(page.getMetaPageId() + "/leadgen_forms", token, formQuery);
            log.info("Lead backfill: page {} has {} lead form(s)", page.getMetaPageId(), forms.path("data").size());
            for (JsonNode form : forms.path("data")) {
                String formId = Jsons.text(form.path("id"));
                if (formId == null) {
                    continue;
                }
                try {
                    backfillForm(page, connection, token, formId, since);
                } catch (MetaApiException ex) {
                    if (ex.rateLimited()) {
                        throw ex;
                    }
                    // One form can fail (no access to it) while the others work.
                    log.warn("Lead backfill: form {} failed: {} {}", formId, ex.getMessage(), ex.detail());
                    if (ex.code() == 200 || ex.code() == 10) {
                        log.warn("Lead backfill: Meta denied lead access. The token needs leads_retrieval and the "
                                + "connected user needs Leads Access on this page; reconnect Meta after fixing it.");
                        logPermissions(connection);
                        return;
                    }
                }
            }
            formAfter = nextCursor(forms);
            if (formAfter == null) {
                break;
            }
        }
    }

    private void backfillForm(MetaPage page, MetaConnection connection, String token, String formId, String since) {
        String after = null;
        for (int pageNo = 0; pageNo < 30; pageNo++) {
            Map<String, String> query = new java.util.LinkedHashMap<>();
            query.put("fields", "id,created_time,ad_id,form_id,field_data,campaign_id,adset_id,is_test");
            query.put("limit", "100");
            if (since != null) {
                query.put("filtering", "[{\"field\":\"time_created\",\"operator\":\"GREATER_THAN\",\"value\":" + since + "}]");
            }
            if (after != null) {
                query.put("after", after);
            }
            JsonNode leadPage = graph.get(formId + "/leads", token, query);
            log.info("Lead backfill: form {} returned {} lead(s)", formId, leadPage.path("data").size());
            for (JsonNode leadJson : leadPage.path("data")) {
                try {
                    self.getObject().store(page, connection, leadJson, page.getMetaPageId(), formId, null, null, true);
                } catch (RuntimeException ex) {
                    log.warn("Skipping lead during backfill: {} {}", ex.getClass().getSimpleName(), ex.getMessage());
                }
            }
            after = nextCursor(leadPage);
            if (after == null) {
                break;
            }
        }
    }

    /**
     * Leads flagged spam only because their form used custom question keys have no name, phone or email.
     * Re-reads their stored Meta payload with the tolerant normalizer and un-flags the real ones.
     */
    @Transactional
    public void repairUnparsedLeads(long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null) {
            return;
        }
        int fixed = 0;
        for (Lead lead : leads.findByTenantIdAndSpamTrueAndNameIsNullAndPhoneNormalizedIsNullAndEmailNormalizedIsNull(tenantId)) {
            if (lead.getRawPayload() == null || !Codes.LeadStatus.SPAM.equals(lead.getStatus())) {
                continue;
            }
            LeadNormalizer.Result result = LeadNormalizer.normalize(objectMapper.readTree(lead.getRawPayload()), tenant.getCountryCode());
            if (result.spam()) {
                continue;
            }
            lead.setName(result.name());
            lead.setPhoneNormalized(result.phone());
            lead.setEmailNormalized(result.email());
            lead.setSpam(false);
            lead.setStatus(Codes.LeadStatus.NEW);
            leads.save(lead);
            LeadStatusHistory change = new LeadStatusHistory();
            change.setTenantId(tenantId);
            change.setLeadId(lead.getId());
            change.setFromStatus(Codes.LeadStatus.SPAM);
            change.setToStatus(Codes.LeadStatus.NEW);
            change.setChangedAt(Instant.now());
            history.save(change);
            fixed++;
        }
        if (fixed > 0) {
            log.info("Lead repair: {} lead(s) were wrongly marked spam and are now NEW", fixed);
        }
    }

    /** Logs which permissions the stored token really has, to see what is missing. */
    private void logPermissions(MetaConnection connection) {
        try {
            String token = crypto.decrypt(connection.getEncryptedAccessToken());
            JsonNode perms = graph.get("me/permissions", token, Map.of("limit", "100"));
            StringBuilder granted = new StringBuilder();
            StringBuilder declined = new StringBuilder();
            for (JsonNode node : perms.path("data")) {
                String name = Jsons.text(node.path("permission"));
                String status = Jsons.text(node.path("status"));
                (("granted".equals(status)) ? granted : declined).append(name).append(' ');
            }
            log.warn("Lead backfill: token permissions granted=[{}] declined/expired=[{}]", granted.toString().trim(), declined.toString().trim());
        } catch (Exception ex) {
            log.warn("Lead backfill: could not read token permissions: {}", ex.getMessage());
        }
        try {
            // debug_token shows the per-page (granular) permissions, which Facebook Login for Business uses.
            String token = crypto.decrypt(connection.getEncryptedAccessToken());
            String appToken = properties.meta().appId() + "|" + properties.meta().appSecret();
            JsonNode data = graph.get("debug_token", appToken, Map.of("input_token", token)).path("data");
            StringBuilder granular = new StringBuilder();
            for (JsonNode g : data.path("granular_scopes")) {
                granular.append(Jsons.text(g.path("scope"))).append("->pages[");
                for (JsonNode target : g.path("target_ids")) {
                    granular.append(Jsons.text(target)).append(' ');
                }
                granular.append("] ");
            }
            log.warn("Lead backfill: debug_token valid={} type={} scopes={} granular={}",
                    data.path("is_valid").asBoolean(false), Jsons.text(data.path("type")), data.path("scopes"), granular.toString().trim());
        } catch (Exception ex) {
            log.warn("Lead backfill: debug_token failed: {}", ex.getMessage());
        }
    }

    private static String nextCursor(JsonNode page) {
        JsonNode next = page.path("paging").path("next");
        if (next.isMissingNode() || next.isNull()) {
            return null;
        }
        return Jsons.text(page.path("paging").path("cursors").path("after"));
    }

    @Transactional
    public void finish(WebhookEvent event, Long tenantId, String status, String error) {
        event.setTenantId(tenantId);
        event.setStatus(status);
        event.setErrorCode(error);
        event.setProcessedAt(Instant.now());
        events.save(event);
    }

    private void markUnhealthy(MetaConnection connection, String code) {
        connection.setStatus("UNHEALTHY");
        connection.setHealthMessage(code);
        connection.setUpdatedAt(Instant.now());
        connections.save(connection);
        for (Long userId : users.idsWithPermission(connection.getTenantId(), Codes.Permission.META_MANAGE)) {
            notifications.notifyUser(connection.getTenantId(), userId, "Meta connection needs attention",
                    "Reconnect Meta. Lead sync has stopped for this connection.",
                    "meta/connection", "meta-unhealthy:" + connection.getId());
        }
    }

    private void notifyOwners(Lead lead, Tenant tenant, String title, String body, String dedupe) {
        for (Long userId : users.idsWithPermission(tenant.getId(), Codes.Permission.META_MANAGE)) {
            notifications.notifyUser(tenant.getId(), userId, title, body, "lead/" + lead.getPublicId(),
                    dedupe + ":" + userId);
        }
    }

    private void recordAttempt(WebhookEvent event, String error) {
        WebhookAttempt attempt = new WebhookAttempt();
        attempt.setWebhookEventId(event.getId());
        attempt.setAttemptNo(attempts.countByWebhookEventId(event.getId()) + 1);
        attempt.setStatus("FAILED");
        attempt.setErrorCode(error);
        attempt.setErrorMessage(error);
        attempt.setAttemptedAt(Instant.now());
        attempts.save(attempt);
    }

    private static String firstNonBlank(String current, String next) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        return next;
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