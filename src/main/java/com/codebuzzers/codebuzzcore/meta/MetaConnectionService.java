package com.codebuzzers.codebuzzcore.meta;

import com.codebuzzers.codebuzzcore.audit.AuditService;
import com.codebuzzers.codebuzzcore.billing.EntitlementService;
import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.AppProperties;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.CryptoService;
import com.codebuzzers.codebuzzcore.common.Hashes;
import com.codebuzzers.codebuzzcore.common.Jsons;
import com.codebuzzers.codebuzzcore.common.SecretMaterial;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.identity.AppUserRepository;
import com.codebuzzers.codebuzzcore.identity.TenantRepository;
import com.codebuzzers.codebuzzcore.security.AccessGuard;
import com.codebuzzers.codebuzzcore.job.JobQueue;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MetaConnectionService {

    private static final String SCOPES = "leads_retrieval,pages_show_list,pages_manage_metadata,pages_read_engagement,ads_read,business_management";

    private final AppProperties properties;
    private final SecretMaterial secrets;
    private final CryptoService crypto;
    private final MetaGraphClient graph;
    private final MetaConnectionRepository connections;
    private final MetaPageRepository pages;
    private final MetaAdAccountRepository adAccounts;
    private final MetaFormRepository forms;
    private final MetaFormFieldRepository fields;
    private final MetaFieldMappingRepository mappings;
    private final EntitlementService entitlements;
    private final AuditService audit;
    private final AccessGuard access;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final EntityManager em;
    private final JobQueue jobs;

    public MetaConnectionService(AppProperties properties, SecretMaterial secrets, CryptoService crypto, MetaGraphClient graph,
                                 MetaConnectionRepository connections, MetaPageRepository pages, MetaAdAccountRepository adAccounts,
                                 MetaFormRepository forms, MetaFormFieldRepository fields, MetaFieldMappingRepository mappings,
                                 EntitlementService entitlements, AuditService audit, AccessGuard access,
                                 TenantRepository tenants, AppUserRepository users, EntityManager em, JobQueue jobs) {
        this.properties = properties;
        this.secrets = secrets;
        this.crypto = crypto;
        this.graph = graph;
        this.connections = connections;
        this.pages = pages;
        this.adAccounts = adAccounts;
        this.forms = forms;
        this.fields = fields;
        this.mappings = mappings;
        this.entitlements = entitlements;
        this.audit = audit;
        this.access = access;
        this.tenants = tenants;
        this.users = users;
        this.em = em;
        this.jobs = jobs;
    }

    public Map<String, Object> authorizationUrl() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.META_MANAGE);
        requireApp();
        String state = signState(ctx.tenantPublicId(), ctx.userPublicId());

        StringBuilder url = new StringBuilder("https://www.facebook.com/")
                .append(properties.meta().graphVersion())
                .append("/dialog/oauth?client_id=").append(enc(properties.meta().appId()))
                .append("&redirect_uri=").append(enc(properties.meta().oauthRedirectUri()))
                .append("&state=").append(enc(state))
                .append("&response_type=code");

        String configId = properties.meta().configId();
        if (configId != null && !configId.isBlank()) {
            // Facebook Login for Business: permissions come from the configuration
            url.append("&config_id=").append(enc(configId))
                    .append("&override_default_response_type=true");
        } else {
            // Classic Facebook Login fallback
            url.append("&scope=").append(enc(SCOPES));
        }
        return Map.of("authorizationUrl", url.toString());
    }

    @Transactional
    public String callback(String code, String state) {
        ParsedState parsed = parseState(state);
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("Meta did not return an authorization code");
        }
        requireApp();
        JsonNode shortToken = graph.getPublic("oauth/access_token", Map.of(
                "client_id", properties.meta().appId(),
                "redirect_uri", properties.meta().oauthRedirectUri(),
                "client_secret", properties.meta().appSecret(),
                "code", code));
        String userToken = Jsons.text(shortToken.path("access_token"));
        if (userToken == null) {
            throw ApiException.badRequest("Meta did not return an access token");
        }
        JsonNode longToken = graph.getPublic("oauth/access_token", Map.of(
                "grant_type", "fb_exchange_token",
                "client_id", properties.meta().appId(),
                "client_secret", properties.meta().appSecret(),
                "fb_exchange_token", userToken));
        String accessToken = Jsons.text(longToken.path("access_token"));
        if (accessToken == null) {
            accessToken = userToken;
        }
        JsonNode me = graph.get("me", accessToken, Map.of("fields", "id,name"));
        var tenant = parsed.tenantId();
        var userId = parsed.userId();
        connections.findByTenantIdOrderByIdDesc(tenant).stream()
                .filter(row -> "ACTIVE".equals(row.getStatus()) || "UNHEALTHY".equals(row.getStatus()))
                .forEach(row -> disconnect(row));
        MetaConnection connection = new MetaConnection();
        connection.setTenantId(tenant);
        connection.setMetaUserId(Jsons.text(me.path("id")));
        connection.setMetaUserName(Jsons.text(me.path("name")));
        connection.setEncryptedAccessToken(crypto.encrypt(accessToken));
        connection.setTokenExpiresAt(Instant.now().plusSeconds(60L * 24 * 60 * 60));
        connection.setGrantedScopes(SCOPES);
        connection.setStatus("ACTIVE");
        connection.setConnectedByUserId(userId);
        connections.save(connection);
        JsonNode pageList = graph.get("me/accounts", accessToken, Map.of("fields", "id,name,access_token", "limit", "100"));
        for (JsonNode page : pageList.path("data")) {
            String metaPageId = Jsons.text(page.path("id"));
            if (metaPageId == null) {
                continue;
            }
            // Upsert: a page row may already exist from a previous (now disconnected) connection
            MetaPage row = findPage(tenant, metaPageId);
            if (row == null) {
                row = new MetaPage();
                row.setTenantId(tenant);
                row.setMetaPageId(metaPageId);
            }
            String pageToken = Jsons.text(page.path("access_token"));
            row.setConnectionId(connection.getId());
            row.setName(Jsons.text(page.path("name")) == null ? "Page" : Jsons.text(page.path("name")));
            row.setEncryptedPageToken(pageToken == null ? null : crypto.encrypt(pageToken));
            row.setStatus("DISCOVERED");
            pages.save(row);
        }
        JsonNode accounts = graph.get("me/adaccounts", accessToken, Map.of("fields", "id,name,currency", "limit", "100"));
        for (JsonNode account : accounts.path("data")) {
            String metaAdAccountId = Jsons.text(account.path("id"));
            if (metaAdAccountId == null) {
                continue;
            }
            MetaAdAccount row = findAdAccount(tenant, metaAdAccountId);
            if (row == null) {
                row = new MetaAdAccount();
                row.setTenantId(tenant);
                row.setMetaAdAccountId(metaAdAccountId);
            }
            row.setConnectionId(connection.getId());
            row.setName(Jsons.text(account.path("name")) == null ? "Ad account" : Jsons.text(account.path("name")));
            row.setCurrencyCode(Jsons.text(account.path("currency")));
            row.setStatus("DISCOVERED");
            adAccounts.save(row);
        }
        audit.record(tenant, userId, "META_CONNECTED", "META_CONNECTION", connection.getPublicId(), null, null, Codes.Source.PLATFORM);
        if (properties.meta().successRedirect() != null && !properties.meta().successRedirect().isBlank()) {
            return "redirect:" + properties.meta().successRedirect();
        }
        return "html";
    }

    @Transactional(readOnly = true)
    public Map<String, Object> current() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.META_MANAGE);
        MetaConnection connection = connections.findFirstByTenantIdAndStatusOrderByIdDesc(ctx.tenantId(), "ACTIVE")
                .or(() -> connections.findFirstByTenantIdAndStatusOrderByIdDesc(ctx.tenantId(), "UNHEALTHY"))
                .orElse(null);
        if (connection == null) {
            return Map.of("connected", false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", true);
        body.put("id", connection.getPublicId());
        body.put("status", connection.getStatus());
        body.put("metaUserId", connection.getMetaUserId());
        body.put("metaUserName", connection.getMetaUserName());
        body.put("healthMessage", connection.getHealthMessage());
        body.put("tokenExpiresAt", connection.getTokenExpiresAt());
        body.put("pages", pages.findByTenantIdAndConnectionId(ctx.tenantId(), connection.getId()).stream().map(this::pageView).toList());
        body.put("adAccounts", adAccounts.findByTenantIdAndConnectionId(ctx.tenantId(), connection.getId()).stream().map(this::accountView).toList());
        return body;
    }

    @Transactional
    public Map<String, Object> selectAssets(List<String> pageIds, List<String> adAccountIds) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.META_MANAGE);
        MetaConnection connection = connections.findFirstByTenantIdAndStatusOrderByIdDesc(ctx.tenantId(), "ACTIVE")
                .orElseThrow(() -> ApiException.badRequest("Connect Meta before selecting assets"));
        Set<String> selectedPages = Set.copyOf(pageIds == null ? List.of() : pageIds);
        Set<String> selectedAccounts = Set.copyOf(adAccountIds == null ? List.of() : adAccountIds);
        entitlements.requireAdAccountSlot(ctx.tenantId(), selectedAccounts.size());
        for (MetaPage page : pages.findByTenantIdAndConnectionId(ctx.tenantId(), connection.getId())) {
            boolean selected = selectedPages.contains(page.getMetaPageId());
            page.setStatus(selected ? "SELECTED" : "IGNORED");
            if (selected) {
                subscribe(page);
                syncForms(page);
            }
        }
        for (MetaAdAccount account : adAccounts.findByTenantIdAndConnectionId(ctx.tenantId(), connection.getId())) {
            account.setStatus(selectedAccounts.contains(account.getMetaAdAccountId()) ? "SELECTED" : "IGNORED");
        }
        // Pull the newly selected ad accounts right away instead of waiting for the hourly sync.
        jobs.enqueue(ctx.tenantId(), Codes.JobType.SYNC_CAMPAIGNS,
                "sync-now:" + connection.getId() + ":" + System.currentTimeMillis(),
                "{\"connectionId\":" + connection.getId() + "}");
        audit.record(ctx.tenantId(), ctx.userId(), "META_ASSETS_SELECTED", "META_CONNECTION", connection.getPublicId(), null,
                "{\"pages\":" + selectedPages.size() + ",\"adAccounts\":" + selectedAccounts.size() + "}", Codes.Source.ANDROID);
        return current();
    }

    @Transactional
    public void disconnectCurrent() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.META_MANAGE);
        MetaConnection connection = connections.findFirstByTenantIdAndStatusOrderByIdDesc(ctx.tenantId(), "ACTIVE")
                .or(() -> connections.findFirstByTenantIdAndStatusOrderByIdDesc(ctx.tenantId(), "UNHEALTHY"))
                .orElseThrow(() -> ApiException.notFound("No Meta connection"));
        disconnect(connection);
        audit.record(ctx.tenantId(), ctx.userId(), "META_DISCONNECTED", "META_CONNECTION", connection.getPublicId(), null, null, Codes.Source.ANDROID);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForms() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.META_MANAGE);
        return forms.findByTenantId(ctx.tenantId()).stream().map(form -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", form.getMetaFormId());
            body.put("name", form.getName());
            body.put("status", form.getStatus());
            body.put("version", form.getVersion());
            body.put("mappings", mappings.findByFormId(form.getId()).stream()
                    .map(mapping -> Map.of("source", mapping.getSourceField(), "target", mapping.getTargetField()))
                    .toList());
            return body;
        }).toList();
    }

    @Transactional
    public void replaceMappings(String metaFormId, List<Map<String, String>> rows) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.META_MANAGE);
        MetaForm form = forms.findByTenantIdAndMetaFormId(ctx.tenantId(), metaFormId)
                .orElseThrow(() -> ApiException.notFound("Form not found"));
        mappings.deleteByFormId(form.getId());
        em.flush(); // make sure deletes hit the DB before the inserts below
        for (Map<String, String> row : rows) {
            MetaFieldMapping mapping = new MetaFieldMapping();
            mapping.setFormId(form.getId());
            mapping.setSourceField(row.get("source"));
            mapping.setTargetField(row.get("target"));
            mappings.save(mapping);
        }
        form.setVersion(form.getVersion() + 1);
    }

    private void subscribe(MetaPage page) {
        String token = crypto.decrypt(page.getEncryptedPageToken());
        if (token == null) {
            return;
        }
        try {
            graph.postForm(page.getMetaPageId() + "/subscribed_apps", token, Map.of("subscribed_fields", "leadgen"));
        } catch (MetaApiException ex) {
            page.setStatus("SELECTED");
        }
    }

    private void syncForms(MetaPage page) {
        String token = crypto.decrypt(page.getEncryptedPageToken());
        if (token == null) {
            return;
        }
        JsonNode body;
        try {
            body = graph.get(page.getMetaPageId() + "/leadgen_forms", token, Map.of("fields", "id,name,status,questions", "limit", "100"));
        } catch (MetaApiException ex) {
            return;
        }
        for (JsonNode formNode : body.path("data")) {
            String metaFormId = Jsons.text(formNode.path("id"));
            MetaForm form = forms.findByTenantIdAndMetaFormId(page.getTenantId(), metaFormId).orElseGet(MetaForm::new);
            boolean created = form.getId() == null;
            form.setTenantId(page.getTenantId());
            form.setPageId(page.getId());
            form.setMetaFormId(metaFormId);
            form.setName(Jsons.text(formNode.path("name")) == null ? "Form" : Jsons.text(formNode.path("name")));
            form.setStatus(Jsons.text(formNode.path("status")) == null ? "ACTIVE" : Jsons.text(formNode.path("status")));
            forms.save(form);
            if (!created) {
                fields.deleteByFormId(form.getId());
                // Derived deletes are only queued; IDENTITY inserts run immediately.
                // Flush so old rows are gone before re-inserting (fixes uk_form_field violation).
                em.flush();
            }
            Set<String> seenKeys = new HashSet<>();
            for (JsonNode question : formNode.path("questions")) {
                String key = Jsons.text(question.path("key"));
                if (key == null || !seenKeys.add(key)) {
                    continue;
                }
                MetaFormField field = new MetaFormField();
                field.setFormId(form.getId());
                field.setMetaFieldKey(key);
                field.setLabel(Jsons.text(question.path("label")) == null ? key : Jsons.text(question.path("label")));
                field.setFieldType(Jsons.text(question.path("type")));
                fields.save(field);
                if (mappings.findByFormIdAndSourceField(form.getId(), key).isEmpty() && defaultTarget(key) != null) {
                    MetaFieldMapping mapping = new MetaFieldMapping();
                    mapping.setFormId(form.getId());
                    mapping.setSourceField(key);
                    mapping.setTargetField(defaultTarget(key));
                    mappings.save(mapping);
                }
            }
        }
    }

    private MetaPage findPage(Long tenantId, String metaPageId) {
        return em.createQuery("select p from MetaPage p where p.tenantId = :t and p.metaPageId = :id", MetaPage.class)
                .setParameter("t", tenantId)
                .setParameter("id", metaPageId)
                .setMaxResults(1)
                .getResultStream().findFirst().orElse(null);
    }

    private MetaAdAccount findAdAccount(Long tenantId, String metaAdAccountId) {
        return em.createQuery("select a from MetaAdAccount a where a.tenantId = :t and a.metaAdAccountId = :id", MetaAdAccount.class)
                .setParameter("t", tenantId)
                .setParameter("id", metaAdAccountId)
                .setMaxResults(1)
                .getResultStream().findFirst().orElse(null);
    }

    private void disconnect(MetaConnection connection) {
        connection.setStatus("DISCONNECTED");
        connection.setEncryptedAccessToken(null);
        connection.setDisconnectedAt(Instant.now());
        connection.setUpdatedAt(Instant.now());
        for (MetaPage page : pages.findByTenantIdAndConnectionId(connection.getTenantId(), connection.getId())) {
            page.setEncryptedPageToken(null);
            page.setStatus("IGNORED");
        }
    }

    private Map<String, Object> pageView(MetaPage page) {
        return Map.of("id", page.getMetaPageId(), "name", page.getName(), "status", page.getStatus());
    }

    private Map<String, Object> accountView(MetaAdAccount account) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", account.getMetaAdAccountId());
        body.put("name", account.getName());
        body.put("currency", account.getCurrencyCode());
        body.put("status", account.getStatus());
        return body;
    }

    private String signState(String tenantPublicId, String userPublicId) {
        String payload = tenantPublicId + "|" + userPublicId + "|" + Instant.now().plusSeconds(15 * 60).getEpochSecond();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + hmac(encoded);
    }

    private ParsedState parseState(String state) {
        if (state == null || !state.contains(".")) {
            throw ApiException.badRequest("OAuth state is invalid");
        }
        String[] parts = state.split("\\.", 2);
        if (!constantTime(hmac(parts[0]), parts[1])) {
            throw ApiException.badRequest("OAuth state is invalid");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|");
        if (fields.length != 3 || Long.parseLong(fields[2]) < Instant.now().getEpochSecond()) {
            throw ApiException.badRequest("OAuth state has expired");
        }
        var tenant = tenants.findByPublicId(fields[0]).orElseThrow(() -> ApiException.badRequest("OAuth state is invalid"));
        var user = users.findByPublicId(fields[1]).filter(row -> row.getTenantId().equals(tenant.getId()))
                .orElseThrow(() -> ApiException.badRequest("OAuth state is invalid"));
        return new ParsedState(tenant.getId(), user.getId());
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secrets.jwtSecret(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign OAuth state", e);
        }
    }

    private static boolean constantTime(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void requireApp() {
        if (properties.meta().appId() == null || properties.meta().appId().isBlank()
                || properties.meta().appSecret() == null || properties.meta().appSecret().isBlank()) {
            throw ApiException.unavailable("META_APP_ID and META_APP_SECRET are not configured");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String defaultTarget(String key) {
        return switch (key) {
            case "full_name", "first_name" -> "name";
            case "email" -> "email";
            case "phone_number" -> "phone";
            default -> null;
        };
    }

    private record ParsedState(long tenantId, long userId) {
    }
}