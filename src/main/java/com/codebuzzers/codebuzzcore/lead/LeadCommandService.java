package com.codebuzzers.codebuzzcore.lead;

import com.codebuzzers.codebuzzcore.audit.AuditService;
import com.codebuzzers.codebuzzcore.campaign.MetaCampaignRepository;
import com.codebuzzers.codebuzzcore.billing.EntitlementService;
import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.PageResponse;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.identity.AppUser;
import com.codebuzzers.codebuzzcore.identity.AppUserRepository;
import com.codebuzzers.codebuzzcore.job.IdempotencyService;
import com.codebuzzers.codebuzzcore.meta.MetaAdAccount;
import com.codebuzzers.codebuzzcore.meta.MetaAdAccountRepository;
import com.codebuzzers.codebuzzcore.security.AccessGuard;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class LeadCommandService {

    private static final Set<String> QUALIFYING = Set.of("CONNECTED", "MANUAL_CONTACT", "WHATSAPP_REPLY");
    private static final Set<String> CALL_OUTCOMES = Set.of("CONNECTED", "NO_ANSWER", "BUSY", "VOICEMAIL", "MANUAL_CONTACT", "WHATSAPP_REPLY");

    private final LeadRepository leads;
    private final LeadAccess access;
    private final LeadNoteRepository notes;
    private final LeadActivityRepository activities;
    private final LeadTaskRepository tasks;
    private final LeadStatusHistoryRepository history;
    private final LeadAssignmentRepository assignments;
    private final LeadSaleRepository sales;
    private final LeadLossRepository losses;
    private final LossReasonRepository reasons;
    private final AssignmentService assignmentService;
    private final AppUserRepository users;
    private final EntitlementService entitlements;
    private final AuditService audit;
    private final AccessGuard guard;
    private final IdempotencyService idempotency;
    private final MetaCampaignRepository campaigns;
    private final MetaAdAccountRepository adAccounts;

    public LeadCommandService(LeadRepository leads, LeadAccess access, LeadNoteRepository notes,
                              LeadActivityRepository activities, LeadTaskRepository tasks,
                              LeadStatusHistoryRepository history, LeadAssignmentRepository assignments,
                              LeadSaleRepository sales, LeadLossRepository losses, LossReasonRepository reasons,
                              AssignmentService assignmentService, AppUserRepository users,
                              EntitlementService entitlements, AuditService audit, AccessGuard guard,
                              IdempotencyService idempotency, MetaCampaignRepository campaigns,
                              MetaAdAccountRepository adAccounts) {
        this.leads = leads;
        this.access = access;
        this.notes = notes;
        this.activities = activities;
        this.tasks = tasks;
        this.history = history;
        this.assignments = assignments;
        this.sales = sales;
        this.losses = losses;
        this.reasons = reasons;
        this.assignmentService = assignmentService;
        this.users = users;
        this.entitlements = entitlements;
        this.audit = audit;
        this.guard = guard;
        this.idempotency = idempotency;
        this.campaigns = campaigns;
        this.adAccounts = adAccounts;
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, int page, int size) {
        LeadAccess.Scope scope = access.requireRead();
        var result = leads.findAll(specification(scope, status), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.getContent().stream().map(lead -> enrich(lead, summary(lead))).toList(), page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String publicId) {
        Lead lead = visible(access.requireRead(), publicId);
        Map<String, Object> body = summary(lead);
        body.put("email", lead.getEmailNormalized());
        body.put("phone", lead.getPhoneNormalized());
        body.put("metaLeadId", lead.getMetaLeadId());
        body.put("metaPageId", lead.getMetaPageId());
        body.put("metaFormId", lead.getMetaFormId());
        body.put("metaAdAccountId", lead.getMetaAdAccountId());
        body.put("metaAdsetId", lead.getMetaAdsetId());
        body.put("metaAdId", lead.getMetaAdId());
        body.put("firstResponseAt", lead.getFirstResponseAt());
        body.put("lastContactAt", lead.getLastContactAt());
        body.put("overQuota", lead.isOverQuota());
        body.put("rawPayload", lead.getRawPayload());
        return enrich(lead, body);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> timeline(String publicId) {
        Lead lead = visible(access.requireRead(), publicId);
        List<Map<String, Object>> rows = new ArrayList<>();
        history.findByTenantIdAndLeadIdOrderByChangedAtAsc(lead.getTenantId(), lead.getId()).forEach(row ->
                rows.add(event("STATUS", row.getChangedAt(), Map.of("from", String.valueOf(row.getFromStatus()), "to", row.getToStatus()))));
        activities.findByTenantIdAndLeadIdOrderByOccurredAtAsc(lead.getTenantId(), lead.getId()).forEach(row ->
                rows.add(event(row.getType(), row.getOccurredAt(), Map.of("metadata", String.valueOf(row.getMetadataJson())))));
        notes.findByTenantIdAndLeadIdOrderByCreatedAtAsc(lead.getTenantId(), lead.getId()).forEach(row ->
                rows.add(event("NOTE", row.getCreatedAt(), Map.of("body", row.getBody()))));
        assignments.findByTenantIdAndLeadIdOrderByAssignedAtAsc(lead.getTenantId(), lead.getId()).forEach(row ->
                rows.add(event("ASSIGNMENT_RECORD", row.getAssignedAt(), Map.of(
                        "attempt", row.getAttemptNo(), "reason", row.getReason(), "toUserId", row.getToUserId()))));
        rows.sort((a, b) -> String.valueOf(a.get("at")).compareTo(String.valueOf(b.get("at"))));
        return rows;
    }

    @Transactional
    public Map<String, Object> addNote(String publicId, String body) {
        Lead lead = visible(access.requireUpdate(), publicId);
        LeadNote note = new LeadNote();
        note.setTenantId(lead.getTenantId());
        note.setLeadId(lead.getId());
        note.setUserId(TenantContext.require().userId());
        note.setBody(body.trim());
        note.setCreatedAt(Instant.now());
        notes.save(note);
        return Map.of("id", note.getId(), "createdAt", note.getCreatedAt());
    }

    @Transactional
    public Map<String, Object> recordCall(String publicId, String outcome, String note, Instant followUpAt) {
        if (!CALL_OUTCOMES.contains(outcome)) {
            throw ApiException.badRequest("Outcome must be one of " + CALL_OUTCOMES);
        }
        Lead lead = visible(access.requireUpdate(), publicId);
        rejectTerminal(lead);
        Instant now = Instant.now();
        lead.setLastContactAt(now);
        boolean qualifying = QUALIFYING.contains(outcome);
        if (qualifying && lead.getFirstResponseAt() == null) {
            lead.setFirstResponseAt(now);
            assignments.findFirstByLeadIdAndTenantIdOrderByAttemptNoDesc(lead.getId(), lead.getTenantId())
                    .ifPresent(row -> {
                        if (row.getRespondedAt() == null) {
                            row.setRespondedAt(now);
                        }
                    });
        }
        if (qualifying && (Codes.LeadStatus.NEW.equals(lead.getStatus()) || Codes.LeadStatus.ASSIGNED.equals(lead.getStatus()))) {
            changeStatus(lead, Codes.LeadStatus.CONTACTED, TenantContext.require().userId());
        }
        LeadActivity activity = new LeadActivity();
        activity.setTenantId(lead.getTenantId());
        activity.setLeadId(lead.getId());
        activity.setUserId(TenantContext.require().userId());
        activity.setType(Codes.Activity.CALL);
        activity.setMetadataJson("{\"outcome\":\"" + outcome + "\",\"qualifying\":" + qualifying + "}");
        activity.setOccurredAt(now);
        activities.save(activity);
        if (note != null && !note.isBlank()) {
            addNote(publicId, note);
        }
        if (followUpAt != null) {
            createTask(lead, "FOLLOW_UP", followUpAt);
            if (Codes.LeadStatus.CONTACTED.equals(lead.getStatus())) {
                changeStatus(lead, Codes.LeadStatus.FOLLOW_UP, TenantContext.require().userId());
            }
        }
        return summary(lead);
    }

    @Transactional
    public Map<String, Object> markWon(String publicId, long amountMinor) {
        if (amountMinor < 0) {
            throw ApiException.badRequest("Sale amount cannot be negative");
        }
        Lead lead = visible(access.requireUpdate(), publicId);
        rejectTerminal(lead);
        changeStatus(lead, Codes.LeadStatus.WON, TenantContext.require().userId());
        LeadSale sale = new LeadSale();
        sale.setTenantId(lead.getTenantId());
        sale.setLeadId(lead.getId());
        sale.setAmountMinor(amountMinor);
        sale.setCurrencyCode(TenantContext.require().currency());
        sale.setWonAt(Instant.now());
        sales.save(sale);
        audit.record(lead.getTenantId(), TenantContext.require().userId(), "LEAD_WON", "LEAD", lead.getPublicId(),
                null, "{\"amountMinor\":" + amountMinor + "}", Codes.Source.ANDROID);
        return summary(lead);
    }

    @Transactional
    public Map<String, Object> markLost(String publicId, String reasonPublicId, String note) {
        Lead lead = visible(access.requireUpdate(), publicId);
        rejectTerminal(lead);
        LossReason reason = reasons.findByPublicIdAndTenantId(reasonPublicId, lead.getTenantId())
                .orElseThrow(() -> ApiException.badRequest("Loss reason not found"));
        changeStatus(lead, Codes.LeadStatus.LOST, TenantContext.require().userId());
        LeadLoss loss = new LeadLoss();
        loss.setTenantId(lead.getTenantId());
        loss.setLeadId(lead.getId());
        loss.setReasonId(reason.getId());
        loss.setNotes(note);
        loss.setLostAt(Instant.now());
        losses.save(loss);
        return summary(lead);
    }

    @Transactional
    public Map<String, Object> markSpam(String publicId) {
        Lead lead = visible(access.requireUpdate(), publicId);
        rejectTerminal(lead);
        lead.setSpam(true);
        changeStatus(lead, Codes.LeadStatus.SPAM, TenantContext.require().userId());
        return summary(lead);
    }

    @Transactional
    public Map<String, Object> assign(String publicId, String userPublicId) {
        TenantContext.Ctx ctx = guard.require(Codes.Permission.LEAD_ASSIGN);
        Lead lead = visible(access.requireRead(), publicId);
        AppUser target = users.findByPublicId(userPublicId).filter(user -> user.getTenantId().equals(ctx.tenantId()) && Codes.UserStatus.ACTIVE.equals(user.getStatus()))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return idempotency.once(ctx.tenantId(), "LEAD_ASSIGN", () -> {
            assignmentService.assignManual(lead, target.getId(), ctx.userId());
            audit.record(ctx.tenantId(), ctx.userId(), "LEAD_REASSIGNED", "LEAD", lead.getPublicId(), null,
                    "{\"to\":\"" + target.getPublicId() + "\"}", Codes.Source.ANDROID);
            return summary(lead);
        });
    }

    @Transactional
    public Map<String, Object> anonymize(String publicId) {
        guard.require(Codes.Permission.LEAD_VIEW_COMPANY);
        Lead lead = visible(access.requireUpdate(), publicId);
        lead.setName("[redacted]");
        lead.setPhoneNormalized(null);
        lead.setEmailNormalized(null);
        lead.setRawPayload("{}");
        notes.findByTenantIdAndLeadIdOrderByCreatedAtAsc(lead.getTenantId(), lead.getId())
                .forEach(note -> note.setBody("[redacted]"));
        audit.record(lead.getTenantId(), TenantContext.require().userId(), "LEAD_ANONYMIZED", "LEAD", lead.getPublicId(),
                null, null, Codes.Source.ANDROID);
        return summary(lead);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String status) {
        TenantContext.Ctx ctx = guard.require(Codes.Permission.LEAD_EXPORT);
        entitlements.requireFeature(ctx.tenantId(), Codes.Feature.CSV_EXPORT);
        LeadAccess.Scope scope = access.requireRead();
        StringBuilder csv = new StringBuilder("id,createdAt,name,phone,email,status,campaignId,assigneeId\n");
        leads.findAll(specification(scope, status), PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(lead -> csv.append(csvCell(lead.getPublicId())).append(',')
                        .append(lead.getCreatedAt()).append(',')
                        .append(csvCell(lead.getName())).append(',')
                        .append(csvCell(lead.getPhoneNormalized())).append(',')
                        .append(csvCell(lead.getEmailNormalized())).append(',')
                        .append(csvCell(lead.getStatus())).append(',')
                        .append(csvCell(lead.getMetaCampaignId())).append(',')
                        .append(lead.getAssignedUserId() == null ? "" : lead.getAssignedUserId())
                        .append('\n'));
        audit.record(ctx.tenantId(), ctx.userId(), "LEAD_EXPORTED", "LEAD", null, null,
                "{\"status\":\"" + (status == null ? "" : status) + "\"}", Codes.Source.ANDROID);
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> lossReasons() {
        TenantContext.Ctx ctx = access.requireRead().ctx();
        return reasons.findByTenantIdAndActiveTrueOrderByNameAsc(ctx.tenantId()).stream()
                .map(reason -> Map.<String, Object>of("id", reason.getPublicId(), "name", reason.getName()))
                .toList();
    }

    @Transactional
    public Map<String, Object> addLossReason(String name) {
        TenantContext.Ctx ctx = guard.require(Codes.Permission.USER_MANAGE);
        LossReason reason = new LossReason();
        reason.setTenantId(ctx.tenantId());
        reason.setName(name.trim());
        reason.setActive(true);
        reasons.save(reason);
        return Map.of("id", reason.getPublicId(), "name", reason.getName());
    }

    @Transactional
    public Map<String, Object> createTask(String leadPublicId, String type, Instant dueAt) {
        guard.require(Codes.Permission.TASK_MANAGE);
        entitlements.requireFeature(TenantContext.require().tenantId(), Codes.Feature.TASKS);
        Lead lead = visible(access.requireUpdate(), leadPublicId);
        LeadTask task = createTask(lead, type == null ? "FOLLOW_UP" : type, dueAt);
        return taskView(task, lead.getPublicId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myTasks() {
        TenantContext.Ctx ctx = guard.require(Codes.Permission.TASK_MANAGE);
        return tasks.findByTenantIdAndAssignedUserIdAndStatusOrderByDueAtAsc(ctx.tenantId(), ctx.userId(), "OPEN").stream()
                .map(task -> {
                    Lead lead = leads.findById(task.getLeadId()).orElse(null);
                    Map<String, Object> view = taskView(task, lead == null ? null : lead.getPublicId());
                    view.put("leadName", lead == null ? null : lead.getName());
                    view.put("leadPhone", lead == null ? null : lead.getPhoneNormalized());
                    return view;
                }).toList();
    }

    @Transactional
    public Map<String, Object> completeTask(String publicId) {
        TenantContext.Ctx ctx = guard.require(Codes.Permission.TASK_MANAGE);
        LeadTask task = tasks.findByPublicIdAndTenantId(publicId, ctx.tenantId()).orElseThrow(() -> ApiException.notFound("Task not found"));
        if (!task.getAssignedUserId().equals(ctx.userId()) && !ctx.has(Codes.Permission.LEAD_VIEW_COMPANY) && !ctx.has(Codes.Permission.LEAD_VIEW_TEAM)) {
            throw ApiException.forbidden("You cannot complete this task");
        }
        task.setStatus("DONE");
        task.setCompletedAt(Instant.now());
        Lead lead = leads.findById(task.getLeadId()).orElse(null);
        return taskView(task, lead == null ? null : lead.getPublicId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> performance(LocalDate from, LocalDate to) {
        TenantContext.Ctx ctx = guard.require(Codes.Permission.REPORT_VIEW);
        entitlements.requireFeature(ctx.tenantId(), Codes.Feature.PERFORMANCE);
        LeadAccess.Scope scope = access.requireRead();
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Lead> rows = leads.findAll(specification(scope, null)).stream()
                .filter(lead -> !lead.isTest())
                .filter(lead -> !lead.getCreatedAt().isBefore(start) && lead.getCreatedAt().isBefore(end))
                .toList();
        long total = rows.size();
        long eligible = rows.stream().filter(lead -> !lead.isSpam() && !Codes.LeadStatus.DUPLICATE.equals(lead.getStatus())).count();
        long assigned = rows.stream().filter(lead -> lead.getAssignedUserId() != null).count();
        long contacted = rows.stream().filter(lead -> lead.getFirstResponseAt() != null).count();
        long won = rows.stream().filter(lead -> Codes.LeadStatus.WON.equals(lead.getStatus())).count();
        long lost = rows.stream().filter(lead -> Codes.LeadStatus.LOST.equals(lead.getStatus())).count();
        long salesMinor = rows.stream().filter(lead -> Codes.LeadStatus.WON.equals(lead.getStatus()))
                .map(lead -> sales.findByLeadId(lead.getId()).map(LeadSale::getAmountMinor).orElse(0L))
                .reduce(0L, Long::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", to);
        body.put("leads", total);
        body.put("eligibleLeads", eligible);
        body.put("assignedLeads", assigned);
        body.put("contactedLeads", contacted);
        body.put("contactRate", assigned == 0 ? 0 : (double) contacted / assigned);
        body.put("won", won);
        body.put("lost", lost);
        body.put("conversionRate", eligible == 0 ? 0 : (double) won / eligible);
        body.put("salesMinor", salesMinor);
        body.put("currency", ctx.currency());
        body.put("qualifyingContact", "CONNECTED, MANUAL_CONTACT, or WHATSAPP_REPLY. A no-answer or busy call is not a contact.");
        return body;
    }

    private LeadTask createTask(Lead lead, String type, Instant dueAt) {
        if (dueAt == null) {
            throw ApiException.badRequest("dueAt is required");
        }
        LeadTask task = new LeadTask();
        task.setTenantId(lead.getTenantId());
        task.setLeadId(lead.getId());
        task.setAssignedUserId(lead.getAssignedUserId() == null ? TenantContext.require().userId() : lead.getAssignedUserId());
        task.setType(type);
        task.setDueAt(dueAt);
        task.setStatus("OPEN");
        tasks.save(task);
        return task;
    }

    private void changeStatus(Lead lead, String to, long actorId) {
        String from = lead.getStatus();
        lead.setStatus(to);
        LeadStatusHistory row = new LeadStatusHistory();
        row.setTenantId(lead.getTenantId());
        row.setLeadId(lead.getId());
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setChangedBy(actorId);
        row.setChangedAt(Instant.now());
        history.save(row);
    }

    private void rejectTerminal(Lead lead) {
        if (Codes.LeadStatus.terminal(lead.getStatus())) {
            throw ApiException.conflict("This lead is already " + lead.getStatus());
        }
    }

    private Lead visible(LeadAccess.Scope scope, String publicId) {
        Lead lead = leads.findByPublicIdAndTenantId(publicId, scope.ctx().tenantId()).orElse(null);
        access.ensureVisible(scope, lead);
        return lead;
    }

    /**
     * Leads follow the selected Meta ad account(s): leads of campaigns that belong to a different ad
     * account are hidden. The campaign decides, not the lead's own ad account tag, which older rows may have
     * wrong. Leads with no campaign stay visible.
     */
    private Specification<Lead> specification(LeadAccess.Scope scope, String status) {
        long tenantId = scope.ctx().tenantId();
        List<MetaAdAccount> selected = adAccounts.findByTenantIdAndStatus(tenantId, "SELECTED");
        Set<Long> selectedIds = selected.stream().map(MetaAdAccount::getId).collect(Collectors.toSet());
        Set<String> hiddenCampaigns = selected.isEmpty() ? Set.of()
                : campaigns.findByTenantIdOrderByNameAsc(tenantId).stream()
                .filter(c -> !selectedIds.contains(c.getAdAccountId()))
                .map(c -> c.getMetaCampaignId()).collect(Collectors.toSet());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!hiddenCampaigns.isEmpty()) {
                predicates.add(cb.or(root.get("metaCampaignId").isNull(), cb.not(root.get("metaCampaignId").in(hiddenCampaigns))));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (scope.kind() == LeadAccess.ScopeKind.OWN) {
                predicates.add(cb.equal(root.get("assignedUserId"), scope.ctx().userId()));
            } else if (scope.kind() == LeadAccess.ScopeKind.TEAM) {
                Predicate mine = cb.equal(root.get("assignedUserId"), scope.ctx().userId());
                if (scope.teamIds().isEmpty()) {
                    predicates.add(mine);
                } else {
                    predicates.add(cb.or(mine, root.get("assignedTeamId").in(scope.teamIds())));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<String, Object> summary(Lead lead) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", lead.getPublicId());
        body.put("name", lead.getName());
        body.put("status", lead.getStatus());
        body.put("test", lead.isTest());
        body.put("spam", lead.isSpam());
        body.put("campaignId", lead.getMetaCampaignId());
        body.put("createdAt", lead.getCreatedAt());
        body.put("assignedUserId", lead.getAssignedUserId());
        return body;
    }

    /** Fields the agent app shows on lead rows: phone, campaign name and the time left to respond. */
    private Map<String, Object> enrich(Lead lead, Map<String, Object> body) {
        body.put("phone", lead.getPhoneNormalized());
        body.put("campaignName", lead.getMetaCampaignId() == null ? null
                : campaigns.findByTenantIdAndMetaCampaignId(lead.getTenantId(), lead.getMetaCampaignId())
                .map(c -> c.getName()).orElse(null));
        Instant respondBy = null;
        if (lead.getFirstResponseAt() == null && (Codes.LeadStatus.NEW.equals(lead.getStatus())
                || Codes.LeadStatus.ASSIGNED.equals(lead.getStatus()))) {
            respondBy = assignments.findFirstByLeadIdAndTenantIdOrderByAttemptNoDesc(lead.getId(), lead.getTenantId())
                    .filter(a -> a.getRespondedAt() == null && !a.isTimedOut())
                    .map(LeadAssignment::getResponseDeadline).orElse(null);
        }
        body.put("respondBy", respondBy);
        return body;
    }

    private Map<String, Object> taskView(LeadTask task, String leadPublicId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", task.getPublicId());
        body.put("leadId", leadPublicId);
        body.put("type", task.getType());
        body.put("dueAt", task.getDueAt());
        body.put("status", task.getStatus());
        body.put("completedAt", task.getCompletedAt());
        return body;
    }

    private static Map<String, Object> event(String type, Instant at, Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("at", at);
        body.put("data", data);
        return body;
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}