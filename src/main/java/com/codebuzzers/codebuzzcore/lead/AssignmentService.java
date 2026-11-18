package com.codebuzzers.codebuzzcore.lead;

import com.codebuzzers.codebuzzcore.billing.EntitlementService;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.identity.AppUserRepository;
import com.codebuzzers.codebuzzcore.identity.TeamMemberRepository;
import com.codebuzzers.codebuzzcore.identity.Tenant;
import com.codebuzzers.codebuzzcore.identity.TenantRepository;
import com.codebuzzers.codebuzzcore.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AssignmentService {

    private final TenantRepository tenants;
    private final TeamMemberRepository members;
    private final AppUserRepository users;
    private final AssignmentStateRepository states;
    private final LeadAssignmentRepository assignments;
    private final LeadRepository leads;
    private final LeadStatusHistoryRepository history;
    private final LeadActivityRepository activities;
    private final NotificationService notifications;
    private final EntitlementService entitlements;

    public AssignmentService(TenantRepository tenants, TeamMemberRepository members, AppUserRepository users,
                             AssignmentStateRepository states, LeadAssignmentRepository assignments,
                             LeadRepository leads, LeadStatusHistoryRepository history,
                             LeadActivityRepository activities, NotificationService notifications,
                             EntitlementService entitlements) {
        this.tenants = tenants;
        this.members = members;
        this.users = users;
        this.states = states;
        this.assignments = assignments;
        this.leads = leads;
        this.history = history;
        this.activities = activities;
        this.notifications = notifications;
        this.entitlements = entitlements;
    }

    @Transactional
    public void assignNew(Lead lead) {
        if (lead.isTest() || lead.isSpam() || Codes.LeadStatus.DUPLICATE.equals(lead.getStatus())) {
            return;
        }
        if (!entitlements.enabled(lead.getTenantId(), Codes.Feature.ROUND_ROBIN)) {
            notifyAssignees(lead, "Lead waiting", "A new lead is waiting for assignment");
            return;
        }
        Tenant tenant = tenants.findById(lead.getTenantId()).orElse(null);
        if (tenant == null || tenant.getDefaultTeamId() == null) {
            notifyAssignees(lead, "Lead waiting", "A new lead arrived and no default team is configured");
            return;
        }
        assignToNext(lead, tenant, tenant.getDefaultTeamId(), null, "ROUND_ROBIN", 1);
    }

    /**
     * Gives leads that are still waiting (status NEW, nobody assigned) to the next on-duty teammate of the
     * default team. A lead waits when it arrived while nobody was on duty, when the company had no default
     * team yet, or when it was already in the database before assignment existed. Runs every minute, newest
     * leads first, at most 50 per run; a lead nobody can take yet is simply tried again on the next run.
     */
    @Transactional
    public int assignWaiting() {
        int assigned = 0;
        for (Lead candidate : leads.findTop50ByStatusAndAssignedUserIdIsNullAndTestFalseAndSpamFalseOrderByCreatedAtDesc(
                Codes.LeadStatus.NEW)) {
            if (!entitlements.enabled(candidate.getTenantId(), Codes.Feature.ROUND_ROBIN)) {
                continue;
            }
            Tenant tenant = tenants.findById(candidate.getTenantId()).orElse(null);
            if (tenant == null || tenant.getDefaultTeamId() == null) {
                continue;
            }
            Lead lead = leads.lockById(candidate.getId(), candidate.getTenantId()).orElse(null);
            if (lead == null || lead.getAssignedUserId() != null || !Codes.LeadStatus.NEW.equals(lead.getStatus())) {
                continue;
            }
            Long next = choose(tenant.getId(), tenant.getDefaultTeamId(), null);
            if (next == null) {
                continue;
            }
            int attempt = assignments.findFirstByLeadIdAndTenantIdOrderByAttemptNoDesc(lead.getId(), lead.getTenantId())
                    .map(row -> row.getAttemptNo() + 1)
                    .orElse(1);
            writeAssignment(lead, tenant, tenant.getDefaultTeamId(), null, next, "ROUND_ROBIN", attempt, null);
            assigned++;
        }
        return assigned;
    }

    @Transactional
    public void assignManual(Lead lead, long toUserId, long actorId) {
        Tenant tenant = tenants.findById(lead.getTenantId()).orElseThrow();
        int attempt = assignments.findFirstByLeadIdAndTenantIdOrderByAttemptNoDesc(lead.getId(), lead.getTenantId())
                .map(row -> row.getAttemptNo() + 1)
                .orElse(1);
        writeAssignment(lead, tenant, lead.getAssignedTeamId() == null ? tenant.getDefaultTeamId() : lead.getAssignedTeamId(),
                lead.getAssignedUserId(), toUserId, "MANUAL", attempt, actorId);
    }

    @Transactional
    public void reassignOpenLeads(long tenantId, long userId) {
        List<Lead> open = leads.findByTenantIdAndAssignedUserIdAndStatusIn(tenantId, userId, List.of(
                Codes.LeadStatus.NEW, Codes.LeadStatus.ASSIGNED, Codes.LeadStatus.CONTACTED, Codes.LeadStatus.FOLLOW_UP));
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null) {
            return;
        }
        for (Lead lead : open) {
            Long teamId = lead.getAssignedTeamId() == null ? tenant.getDefaultTeamId() : lead.getAssignedTeamId();
            int attempt = assignments.findFirstByLeadIdAndTenantIdOrderByAttemptNoDesc(lead.getId(), tenantId)
                    .map(row -> row.getAttemptNo() + 1)
                    .orElse(1);
            if (teamId == null) {
                lead.setAssignedUserId(null);
                lead.setStatus(Codes.LeadStatus.NEW);
                continue;
            }
            assignToNext(lead, tenant, teamId, userId, "USER_DISABLED", attempt);
        }
    }

    @Transactional
    public int timeoutSweep() {
        List<LeadAssignment> due = assignments.dueTimeouts(Instant.now());
        int moved = 0;
        for (LeadAssignment assignment : due) {
            Lead lead = leads.lockById(assignment.getLeadId(), assignment.getTenantId()).orElse(null);
            if (lead == null || !Codes.LeadStatus.ASSIGNED.equals(lead.getStatus())) {
                continue;
            }
            if (lead.getAssignedUserId() == null || !lead.getAssignedUserId().equals(assignment.getToUserId())) {
                continue;
            }
            assignment.setTimedOut(true);
            Tenant tenant = tenants.findById(lead.getTenantId()).orElse(null);
            if (tenant == null || assignment.getLeadId() == null) {
                continue;
            }
            Long teamId = lead.getAssignedTeamId() == null ? tenant.getDefaultTeamId() : lead.getAssignedTeamId();
            if (teamId == null) {
                continue;
            }
            Long next = choose(lead.getTenantId(), teamId, assignment.getToUserId());
            if (next == null || next.equals(assignment.getToUserId())) {
                assignment.setTimedOut(false);
                assignment.setResponseDeadline(Instant.now().plus(tenant.getAssignmentTimeoutMinutes(), ChronoUnit.MINUTES));
                continue;
            }
            writeAssignment(lead, tenant, teamId, assignment.getToUserId(), next, "TIMEOUT", assignment.getAttemptNo() + 1, null);
            moved++;
        }
        return moved;
    }

    private void assignToNext(Lead lead, Tenant tenant, long teamId, Long excludeUserId, String reason, int attempt) {
        Long next = choose(tenant.getId(), teamId, excludeUserId);
        if (next == null) {
            notifyAssignees(lead, "Lead waiting", "No on-duty teammate is available");
            return;
        }
        writeAssignment(lead, tenant, teamId, lead.getAssignedUserId(), next, reason, attempt, null);
    }

    private Long choose(long tenantId, long teamId, Long excludeUserId) {
        AssignmentState state = states.lockTeam(tenantId, teamId).orElseGet(() -> {
            AssignmentState created = new AssignmentState();
            created.setTenantId(tenantId);
            created.setTeamId(teamId);
            created.setVersion(0);
            return states.save(created);
        });
        List<Long> memberIds = members.activeUserIds(tenantId, teamId);
        if (memberIds.isEmpty()) {
            return null;
        }
        List<Long> eligible = users.eligibleIds(tenantId, memberIds);
        if (excludeUserId != null) {
            eligible = eligible.stream().filter(id -> !id.equals(excludeUserId)).toList();
        }
        Long next = RoundRobinPicker.next(eligible, state.getLastAssignedUserId());
        if (next != null) {
            state.setLastAssignedUserId(next);
        }
        return next;
    }

    private void writeAssignment(Lead lead, Tenant tenant, Long teamId, Long fromUserId, long toUserId,
                                 String reason, int attempt, Long actorId) {
        String previous = lead.getStatus();
        Instant now = Instant.now();
        LeadAssignment row = new LeadAssignment();
        row.setTenantId(lead.getTenantId());
        row.setLeadId(lead.getId());
        row.setFromUserId(fromUserId);
        row.setToUserId(toUserId);
        row.setReason(reason);
        row.setAttemptNo(attempt);
        row.setAssignedAt(now);
        row.setResponseDeadline(now.plus(Math.max(tenant.getAssignmentTimeoutMinutes(), 1), ChronoUnit.MINUTES));
        assignments.save(row);

        lead.setAssignedUserId(toUserId);
        lead.setAssignedTeamId(teamId);
        if (!Codes.LeadStatus.terminal(previous) && (Codes.LeadStatus.NEW.equals(previous) || previous == null)) {
            lead.setStatus(Codes.LeadStatus.ASSIGNED);
            addHistory(lead, previous, Codes.LeadStatus.ASSIGNED, actorId, now);
        }
        addActivity(lead, actorId == null ? toUserId : actorId, Codes.Activity.ASSIGNMENT,
                "{\"reason\":\"" + reason + "\",\"toUserId\":" + toUserId + "}", now);
        notifications.notifyUser(lead.getTenantId(), toUserId, "New lead assigned",
                lead.getName() == null ? "A lead is waiting for you" : lead.getName(),
                "lead/" + lead.getPublicId(),
                "lead:" + lead.getId() + ":assigned:" + toUserId + ":attempt:" + attempt);
    }

    private void addHistory(Lead lead, String from, String to, Long actorId, Instant when) {
        LeadStatusHistory row = new LeadStatusHistory();
        row.setTenantId(lead.getTenantId());
        row.setLeadId(lead.getId());
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setChangedBy(actorId);
        row.setChangedAt(when);
        history.save(row);
    }

    private void addActivity(Lead lead, Long userId, String type, String metadata, Instant when) {
        LeadActivity activity = new LeadActivity();
        activity.setTenantId(lead.getTenantId());
        activity.setLeadId(lead.getId());
        activity.setUserId(userId);
        activity.setType(type);
        activity.setMetadataJson(metadata);
        activity.setOccurredAt(when);
        activities.save(activity);
    }

    private void notifyAssignees(Lead lead, String title, String body) {
        for (Long userId : users.idsWithPermission(lead.getTenantId(), Codes.Permission.LEAD_ASSIGN)) {
            notifications.notifyUser(lead.getTenantId(), userId, title, body, "lead/" + lead.getPublicId(),
                    "lead:" + lead.getId() + ":unassigned:" + userId);
        }
    }
}