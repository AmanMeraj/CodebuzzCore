package com.codebuzzers.codebuzzcore.workforce;

import com.codebuzzers.codebuzzcore.audit.AuditService;
import com.codebuzzers.codebuzzcore.billing.EntitlementService;
import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.identity.AppUser;
import com.codebuzzers.codebuzzcore.identity.AppUserRepository;
import com.codebuzzers.codebuzzcore.identity.Team;
import com.codebuzzers.codebuzzcore.identity.TeamMemberRepository;
import com.codebuzzers.codebuzzcore.identity.TeamRepository;
import com.codebuzzers.codebuzzcore.lead.Lead;
import com.codebuzzers.codebuzzcore.lead.LeadActivityRepository;
import com.codebuzzers.codebuzzcore.lead.LeadAssignmentRepository;
import com.codebuzzers.codebuzzcore.lead.LeadRepository;
import com.codebuzzers.codebuzzcore.lead.LeadSale;
import com.codebuzzers.codebuzzcore.lead.LeadSaleRepository;
import com.codebuzzers.codebuzzcore.lead.LeadTask;
import com.codebuzzers.codebuzzcore.lead.LeadTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent profile (attendance, leave, performance), team detail and leave workflow.
 *
 * Attendance is taken from duty sessions: a day counts as present when the user was on duty at any time
 * that day (company time zone). Sunday is the weekly off and is not a working day.
 */
@Service
public class WorkforceService {

    private static final DayOfWeek WEEKLY_OFF = DayOfWeek.SUNDAY;
    private static final int MAX_RANGE_DAYS = 92;
    private static final int MAX_LEAVE_DAYS = 60;
    /** A session left open (forgot to go off duty) counts for at most this long. */
    private static final Duration MAX_SESSION = Duration.ofHours(16);
    private static final Set<String> LEAVE_TYPES = Set.of("CASUAL", "SICK", "PAID", "UNPAID", "OTHER");
    private static final List<String> ROLE_ORDER = List.of("OWNER", "MANAGER", "EMPLOYEE", "VIEWER");

    private final AppUserRepository users;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final DutySessionRepository sessions;
    private final LeaveRecordRepository leaves;
    private final LeadRepository leadRows;
    private final LeadAssignmentRepository assignments;
    private final LeadActivityRepository activities;
    private final LeadTaskRepository tasks;
    private final LeadSaleRepository sales;
    private final EntitlementService entitlements;
    private final AuditService audit;

    public WorkforceService(AppUserRepository users, TeamRepository teams, TeamMemberRepository members,
                            DutySessionRepository sessions, LeaveRecordRepository leaves, LeadRepository leadRows,
                            LeadAssignmentRepository assignments, LeadActivityRepository activities,
                            LeadTaskRepository tasks, LeadSaleRepository sales, EntitlementService entitlements,
                            AuditService audit) {
        this.users = users;
        this.teams = teams;
        this.members = members;
        this.sessions = sessions;
        this.leaves = leaves;
        this.leadRows = leadRows;
        this.assignments = assignments;
        this.activities = activities;
        this.tasks = tasks;
        this.sales = sales;
        this.entitlements = entitlements;
        this.audit = audit;
    }

    // ── Duty sessions (called by AccountService when duty changes) ────────

    @Transactional
    public void dutyChanged(long tenantId, long userId, boolean onDuty) {
        Optional<DutySession> open = sessions.findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId);
        if (onDuty) {
            if (open.isEmpty()) {
                DutySession session = new DutySession();
                session.setTenantId(tenantId);
                session.setUserId(userId);
                session.setStartedAt(Instant.now());
                sessions.save(session);
            }
        } else {
            open.ifPresent(session -> session.setEndedAt(Instant.now()));
        }
    }

    // ── Agent profile ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> profile(String userPublicId, LocalDate from, LocalDate to) {
        TenantContext.Ctx ctx = TenantContext.require();
        AppUser user = viewable(ctx, userPublicId);
        checkRange(from, to);
        ZoneId zone = EntitlementService.zone(ctx.timezone());
        LocalDate today = LocalDate.now(zone);
        Instant now = Instant.now();
        List<LeaveRecord> allLeaves = leaves.findByTenantIdAndUserIdOrderByStartDateDesc(ctx.tenantId(), user.getId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", userView(user, zone));
        body.put("teams", teamsOf(ctx.tenantId(), user.getId()));
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("attendance", attendance(user, from, to, zone, today, now, approved(allLeaves)));
        body.put("leave", leaveSummary(ctx, allLeaves, today, zone, user));
        if (entitlements.enabled(ctx.tenantId(), Codes.Feature.PERFORMANCE)) {
            body.put("performance", performance(ctx.tenantId(), user.getId(), from, to, zone, now));
        }
        body.put("currency", ctx.currency());
        body.put("canRecordLeave", user.getId().equals(ctx.userId()) || canDecide(ctx, user));
        return body;
    }

    private Map<String, Object> userView(AppUser user, ZoneId zone) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getPublicId());
        view.put("name", user.getName());
        view.put("email", user.getEmail());
        view.put("phone", user.getPhone());
        view.put("status", user.getStatus());
        view.put("onDuty", user.isOnDuty());
        view.put("roles", users.roleNames(user.getId()));
        view.put("joinedAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        view.put("lastLoginAt", user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
        return view;
    }

    private List<Map<String, Object>> teamsOf(long tenantId, long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Team team : teams.findAllById(teams.teamIdsForUser(tenantId, userId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", team.getPublicId());
            row.put("name", team.getName());
            row.put("manager", team.getManagerUserId() != null && team.getManagerUserId() == userId);
            out.add(row);
        }
        return out;
    }

    // ── Attendance ────────────────────────────────────────────────────────

    private Map<String, Object> attendance(AppUser user, LocalDate from, LocalDate to, ZoneId zone, LocalDate today,
                                           Instant now, List<LeaveRecord> approvedLeaves) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        Map<LocalDate, Long> minutesByDay = new HashMap<>();
        for (DutySession s : sessions.findByTenantIdAndUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
                user.getTenantId(), user.getId(), start, end)) {
            Instant cap = s.getStartedAt().plus(MAX_SESSION);
            Instant stop = s.getEndedAt() == null ? (now.isBefore(cap) ? now : cap) : (s.getEndedAt().isBefore(cap) ? s.getEndedAt() : cap);
            long minutes = Math.max(0, Duration.between(s.getStartedAt(), stop).toMinutes());
            minutesByDay.merge(s.getStartedAt().atZone(zone).toLocalDate(), minutes, Long::sum);
        }

        LocalDate joined = user.getCreatedAt() == null ? from : user.getCreatedAt().atZone(zone).toLocalDate();
        int workingDays = 0;
        int presentDays = 0;
        int presentWorkingDays = 0;
        int leaveDays = 0;
        int absentDays = 0;
        long totalMinutes = 0;
        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            boolean off = d.getDayOfWeek() == WEEKLY_OFF;
            boolean onLeave = isOnLeave(approvedLeaves, d);
            long minutes = minutesByDay.getOrDefault(d, 0L);
            String status;
            if (d.isBefore(joined)) {
                status = "NA";
            } else if (d.isAfter(today)) {
                status = onLeave && !off ? "LEAVE" : "UPCOMING";
            } else if (minutes > 0) {
                status = "PRESENT";
            } else if (off) {
                status = "OFF";
            } else if (onLeave) {
                status = "LEAVE";
            } else {
                status = "ABSENT";
            }
            if (!d.isBefore(joined) && !d.isAfter(today)) {
                if (!off) {
                    workingDays++;
                }
                switch (status) {
                    case "PRESENT" -> {
                        presentDays++;
                        totalMinutes += minutes;
                        if (!off) {
                            presentWorkingDays++;
                        }
                    }
                    case "LEAVE" -> leaveDays++;
                    case "ABSENT" -> absentDays++;
                    default -> {
                    }
                }
            }
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("status", status);
            day.put("hours", round1(minutes / 60.0));
            days.add(day);
        }
        int expected = workingDays - leaveDays;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workingDays", workingDays);
        body.put("presentDays", presentDays);
        body.put("leaveDays", leaveDays);
        body.put("absentDays", absentDays);
        body.put("attendanceRate", expected <= 0 ? 0.0 : Math.min(1.0, (double) presentWorkingDays / expected));
        body.put("hoursWorked", round1(totalMinutes / 60.0));
        body.put("avgHoursPerDay", presentDays == 0 ? 0.0 : round1(totalMinutes / 60.0 / presentDays));
        body.put("onDutyNow", user.isOnDuty());
        body.put("lastDutyStartAt", sessions.findFirstByTenantIdAndUserIdOrderByStartedAtDesc(user.getTenantId(), user.getId())
                .map(s -> s.getStartedAt().toString()).orElse(null));
        body.put("weeklyOff", WEEKLY_OFF.name());
        body.put("days", days);
        return body;
    }

    // ── Leave ─────────────────────────────────────────────────────────────

    private Map<String, Object> leaveSummary(TenantContext.Ctx ctx, List<LeaveRecord> all, LocalDate today, ZoneId zone,
                                             AppUser target) {
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        List<LeaveRecord> approved = approved(all);
        Map<String, Integer> byType = new LinkedHashMap<>();
        int takenThisYear = 0;
        int upcoming = 0;
        for (LeaveRecord l : approved) {
            int taken = countDays(l, yearStart, today);
            if (taken > 0) {
                takenThisYear += taken;
                byType.merge(l.getLeaveType(), taken, Integer::sum);
            }
            if (l.getEndDate().isAfter(today)) {
                upcoming += countDays(l, today.plusDays(1), l.getEndDate());
            }
        }
        boolean decider = canDecide(ctx, target);
        List<Map<String, Object>> recent = new ArrayList<>();
        int pending = 0;
        for (LeaveRecord l : all) {
            if ("PENDING".equals(l.getStatus())) {
                pending++;
            }
            if (recent.size() < 20) {
                recent.add(leaveView(ctx, l, decider, today));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("year", today.getYear());
        body.put("takenThisYear", takenThisYear);
        body.put("upcomingDays", upcoming);
        body.put("pendingRequests", pending);
        body.put("byType", byType);
        body.put("recent", recent);
        return body;
    }

    private Map<String, Object> leaveView(TenantContext.Ctx ctx, LeaveRecord l, boolean decider, LocalDate today) {
        boolean pending = "PENDING".equals(l.getStatus());
        boolean mine = l.getRequestedBy().equals(ctx.userId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", l.getPublicId());
        view.put("type", l.getLeaveType());
        view.put("startDate", l.getStartDate().toString());
        view.put("endDate", l.getEndDate().toString());
        view.put("days", l.getDays());
        view.put("reason", l.getReason());
        view.put("status", l.getStatus());
        view.put("decidedByName", l.getDecidedBy() == null ? null
                : users.findById(l.getDecidedBy()).map(AppUser::getName).orElse(null));
        view.put("canDecide", pending && decider);
        view.put("canCancel", (mine || decider)
                && (pending || ("APPROVED".equals(l.getStatus()) && !l.getEndDate().isBefore(today))));
        return view;
    }

    @Transactional
    public Map<String, Object> createLeave(String userPublicId, String type, LocalDate start, LocalDate end, String reason) {
        TenantContext.Ctx ctx = TenantContext.require();
        AppUser target = users.findByPublicId(userPublicId).filter(u -> u.getTenantId().equals(ctx.tenantId()))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        boolean self = target.getId().equals(ctx.userId());
        boolean decider = canDecide(ctx, target);
        if (!self && !decider) {
            throw ApiException.forbidden("You cannot record leave for this user");
        }
        String leaveType = type == null ? "" : type.trim().toUpperCase();
        if (!LEAVE_TYPES.contains(leaveType)) {
            throw ApiException.badRequest("Leave type must be CASUAL, SICK, PAID, UNPAID or OTHER");
        }
        if (start == null || end == null || end.isBefore(start)) {
            throw ApiException.badRequest("End date cannot be before the start date");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_LEAVE_DAYS) {
            throw ApiException.badRequest("A leave can cover at most " + MAX_LEAVE_DAYS + " days");
        }
        LocalDate today = LocalDate.now(EntitlementService.zone(ctx.timezone()));
        if (start.isBefore(today.minusDays(decider ? 365 : 7))) {
            throw ApiException.badRequest(decider ? "Leave cannot start more than a year ago"
                    : "Leave cannot start more than 7 days ago");
        }
        int days = workingDays(start, end);
        if (days == 0) {
            throw ApiException.badRequest("The selected dates only fall on the weekly off");
        }
        if (!leaves.overlapping(ctx.tenantId(), target.getId(), start, end).isEmpty()) {
            throw ApiException.conflict("This user already has leave on some of these dates");
        }
        LeaveRecord leave = new LeaveRecord();
        leave.setTenantId(ctx.tenantId());
        leave.setUserId(target.getId());
        leave.setLeaveType(leaveType);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setDays(days);
        leave.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        leave.setRequestedBy(ctx.userId());
        if (decider) {
            leave.setStatus("APPROVED");
            leave.setDecidedBy(ctx.userId());
            leave.setDecidedAt(Instant.now());
        } else {
            leave.setStatus("PENDING");
        }
        leaves.save(leave);
        audit.record(ctx.tenantId(), ctx.userId(), "LEAVE_CREATED", "LEAVE", leave.getPublicId(), null,
                "{\"status\":\"" + leave.getStatus() + "\"}", Codes.Source.ANDROID);
        return leaveView(ctx, leave, decider, today);
    }

    @Transactional
    public Map<String, Object> decideLeave(String leavePublicId, boolean approve) {
        TenantContext.Ctx ctx = TenantContext.require();
        LeaveRecord leave = requireLeave(ctx, leavePublicId);
        AppUser target = users.findById(leave.getUserId()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!canDecide(ctx, target)) {
            throw ApiException.forbidden("You cannot decide this leave");
        }
        if (!"PENDING".equals(leave.getStatus())) {
            throw ApiException.conflict("This leave is already " + leave.getStatus());
        }
        leave.setStatus(approve ? "APPROVED" : "REJECTED");
        leave.setDecidedBy(ctx.userId());
        leave.setDecidedAt(Instant.now());
        audit.record(ctx.tenantId(), ctx.userId(), approve ? "LEAVE_APPROVED" : "LEAVE_REJECTED", "LEAVE",
                leave.getPublicId(), null, null, Codes.Source.ANDROID);
        return leaveView(ctx, leave, true, LocalDate.now(EntitlementService.zone(ctx.timezone())));
    }

    @Transactional
    public Map<String, Object> cancelLeave(String leavePublicId) {
        TenantContext.Ctx ctx = TenantContext.require();
        LeaveRecord leave = requireLeave(ctx, leavePublicId);
        AppUser target = users.findById(leave.getUserId()).orElseThrow(() -> ApiException.notFound("User not found"));
        boolean decider = canDecide(ctx, target);
        if (!leave.getRequestedBy().equals(ctx.userId()) && !decider) {
            throw ApiException.forbidden("You cannot cancel this leave");
        }
        LocalDate today = LocalDate.now(EntitlementService.zone(ctx.timezone()));
        boolean cancellable = "PENDING".equals(leave.getStatus())
                || ("APPROVED".equals(leave.getStatus()) && !leave.getEndDate().isBefore(today));
        if (!cancellable) {
            throw ApiException.conflict("This leave cannot be cancelled");
        }
        leave.setStatus("CANCELLED");
        audit.record(ctx.tenantId(), ctx.userId(), "LEAVE_CANCELLED", "LEAVE", leave.getPublicId(), null, null,
                Codes.Source.ANDROID);
        return leaveView(ctx, leave, decider, today);
    }

    private LeaveRecord requireLeave(TenantContext.Ctx ctx, String publicId) {
        return leaves.findByPublicIdAndTenantId(publicId, ctx.tenantId())
                .orElseThrow(() -> ApiException.notFound("Leave not found"));
    }

    // ── Performance ───────────────────────────────────────────────────────

    /** Lead work of one agent for leads created in the range and currently assigned to them. */
    private Map<String, Object> performance(long tenantId, long userId, LocalDate from, LocalDate to, ZoneId zone,
                                            Instant now) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Lead> rows = leadRows
                .findByTenantIdAndAssignedUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(tenantId, userId, start, end)
                .stream()
                .filter(lead -> !lead.isTest() && !lead.isSpam() && !Codes.LeadStatus.DUPLICATE.equals(lead.getStatus()))
                .toList();
        long assigned = rows.size();
        long contacted = rows.stream().filter(l -> l.getFirstResponseAt() != null).count();
        long won = rows.stream().filter(l -> Codes.LeadStatus.WON.equals(l.getStatus())).count();
        long lost = rows.stream().filter(l -> Codes.LeadStatus.LOST.equals(l.getStatus())).count();
        long open = rows.stream().filter(l -> Codes.LeadStatus.open(l.getStatus())).count();
        long salesMinor = rows.stream().filter(l -> Codes.LeadStatus.WON.equals(l.getStatus()))
                .map(l -> sales.findByLeadId(l.getId()).map(LeadSale::getAmountMinor).orElse(0L))
                .reduce(0L, Long::sum);
        double avgResponseMinutes = rows.stream().filter(l -> l.getFirstResponseAt() != null)
                .mapToLong(l -> Math.max(0, Duration.between(l.getCreatedAt(), l.getFirstResponseAt()).toMinutes()))
                .average().orElse(0);
        long timedOut = assignments.countByTenantIdAndToUserIdAndTimedOutTrueAndAssignedAtGreaterThanEqualAndAssignedAtLessThan(
                tenantId, userId, start, end);
        long calls = activities.countByTenantIdAndUserIdAndTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                tenantId, userId, Codes.Activity.CALL, start, end);
        List<LeadTask> openTasks = tasks.findByTenantIdAndAssignedUserIdAndStatusOrderByDueAtAsc(tenantId, userId, "OPEN");
        long overdue = openTasks.stream().filter(t -> t.getDueAt().isBefore(now)).count();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assigned", assigned);
        body.put("open", open);
        body.put("contacted", contacted);
        body.put("contactRate", assigned == 0 ? 0.0 : (double) contacted / assigned);
        body.put("won", won);
        body.put("lost", lost);
        body.put("conversionRate", assigned == 0 ? 0.0 : (double) won / assigned);
        body.put("salesMinor", salesMinor);
        body.put("avgFirstResponseMinutes", Math.round(avgResponseMinutes));
        body.put("callsLogged", calls);
        body.put("timedOutAssignments", timedOut);
        body.put("openTasks", openTasks.size());
        body.put("overdueTasks", overdue);
        return body;
    }

    // ── Team detail ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> teamDetail(String teamPublicId, LocalDate from, LocalDate to) {
        TenantContext.Ctx ctx = TenantContext.require();
        if (!ctx.has(Codes.Permission.TEAM_MANAGE) && !ctx.has(Codes.Permission.LEAD_ASSIGN)
                && !ctx.has(Codes.Permission.LEAD_VIEW_TEAM) && !ctx.has(Codes.Permission.USER_MANAGE)) {
            throw ApiException.forbidden("You cannot access this resource");
        }
        checkRange(from, to);
        Team team = teams.findByPublicIdAndTenantId(teamPublicId, ctx.tenantId())
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        ZoneId zone = EntitlementService.zone(ctx.timezone());
        LocalDate today = LocalDate.now(zone);
        Instant now = Instant.now();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        boolean performanceOn = entitlements.enabled(ctx.tenantId(), Codes.Feature.PERFORMANCE);

        Set<Long> onLeaveToday = new HashSet<>();
        for (LeaveRecord l : leaves.findByTenantIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                ctx.tenantId(), "APPROVED", today, today)) {
            onLeaveToday.add(l.getUserId());
        }

        List<Long> memberIds = members.activeUserIds(ctx.tenantId(), team.getId());
        List<Map<String, Object>> memberRows = new ArrayList<>();
        long sumAssigned = 0;
        long sumContacted = 0;
        long sumWon = 0;
        long sumLost = 0;
        long sumSales = 0;
        int onDutyCount = 0;
        int presentToday = 0;
        int leaveToday = 0;
        for (AppUser u : users.findAllById(memberIds)) {
            boolean present = !sessions.findByTenantIdAndUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
                    ctx.tenantId(), u.getId(), todayStart, todayEnd).isEmpty();
            boolean leave = onLeaveToday.contains(u.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getPublicId());
            row.put("name", u.getName());
            row.put("email", u.getEmail());
            row.put("role", topRole(users.roleNames(u.getId())));
            row.put("status", u.getStatus());
            row.put("onDuty", u.isOnDuty());
            row.put("presentToday", present);
            row.put("onLeaveToday", leave);
            row.put("manager", team.getManagerUserId() != null && team.getManagerUserId().equals(u.getId()));
            if (performanceOn) {
                Map<String, Object> perf = performance(ctx.tenantId(), u.getId(), from, to, zone, now);
                row.put("assigned", perf.get("assigned"));
                row.put("contacted", perf.get("contacted"));
                row.put("won", perf.get("won"));
                row.put("lost", perf.get("lost"));
                row.put("salesMinor", perf.get("salesMinor"));
                sumAssigned += (long) perf.get("assigned");
                sumContacted += (long) perf.get("contacted");
                sumWon += (long) perf.get("won");
                sumLost += (long) perf.get("lost");
                sumSales += (long) perf.get("salesMinor");
            }
            if (u.isOnDuty()) {
                onDutyCount++;
            }
            if (present) {
                presentToday++;
            }
            if (leave) {
                leaveToday++;
            }
            memberRows.add(row);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("members", memberRows.size());
        totals.put("onDuty", onDutyCount);
        totals.put("presentToday", presentToday);
        totals.put("onLeaveToday", leaveToday);
        totals.put("assigned", sumAssigned);
        totals.put("contacted", sumContacted);
        totals.put("contactRate", sumAssigned == 0 ? 0.0 : (double) sumContacted / sumAssigned);
        totals.put("won", sumWon);
        totals.put("lost", sumLost);
        totals.put("conversionRate", sumAssigned == 0 ? 0.0 : (double) sumWon / sumAssigned);
        totals.put("salesMinor", sumSales);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", team.getPublicId());
        body.put("name", team.getName());
        body.put("status", team.getStatus());
        AppUser manager = team.getManagerUserId() == null ? null : users.findById(team.getManagerUserId()).orElse(null);
        body.put("managerId", manager == null ? null : manager.getPublicId());
        body.put("managerName", manager == null ? null : manager.getName());
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("currency", ctx.currency());
        body.put("performanceEnabled", performanceOn);
        body.put("totals", totals);
        body.put("members", memberRows);
        return body;
    }

    /** Rename a team and/or change its manager. An empty managerUserId clears the manager. */
    @Transactional
    public Map<String, Object> updateTeam(String teamPublicId, String name, String managerPublicId) {
        TenantContext.Ctx ctx = TenantContext.require();
        if (!ctx.has(Codes.Permission.TEAM_MANAGE)) {
            throw ApiException.forbidden("Missing permission " + Codes.Permission.TEAM_MANAGE);
        }
        Team team = teams.findByPublicIdAndTenantId(teamPublicId, ctx.tenantId())
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        if (name != null && !name.isBlank()) {
            team.setName(name.trim());
        }
        if (managerPublicId != null) {
            if (managerPublicId.isBlank()) {
                team.setManagerUserId(null);
            } else {
                AppUser manager = users.findByPublicId(managerPublicId)
                        .filter(u -> u.getTenantId().equals(ctx.tenantId()))
                        .orElseThrow(() -> ApiException.notFound("User not found"));
                team.setManagerUserId(manager.getId());
            }
        }
        audit.record(ctx.tenantId(), ctx.userId(), "TEAM_UPDATED", "TEAM", team.getPublicId(), null, null,
                Codes.Source.ANDROID);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", team.getPublicId());
        body.put("name", team.getName());
        body.put("status", team.getStatus());
        return body;
    }

    // ── Access and helpers ────────────────────────────────────────────────

    /** Yourself, anyone (USER_MANAGE), or someone in a team you manage or belong to (team permissions). */
    private AppUser viewable(TenantContext.Ctx ctx, String publicId) {
        AppUser target = users.findByPublicId(publicId).filter(u -> u.getTenantId().equals(ctx.tenantId()))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.getId().equals(ctx.userId()) || ctx.has(Codes.Permission.USER_MANAGE)) {
            return target;
        }
        if (ctx.has(Codes.Permission.TEAM_MANAGE) || ctx.has(Codes.Permission.LEAD_VIEW_TEAM)
                || ctx.has(Codes.Permission.LEAD_ASSIGN)) {
            for (Long teamId : teams.teamIdsForUser(ctx.tenantId(), ctx.userId())) {
                if (members.activeUserIds(ctx.tenantId(), teamId).contains(target.getId())) {
                    return target;
                }
            }
            throw ApiException.forbidden("This user is not in your team");
        }
        throw ApiException.forbidden("You cannot view this user");
    }

    /** Owners decide for anyone; managers decide for others in their teams, never for themselves. */
    private boolean canDecide(TenantContext.Ctx ctx, AppUser target) {
        if (ctx.has(Codes.Permission.USER_MANAGE)) {
            return true;
        }
        if (!ctx.has(Codes.Permission.TEAM_MANAGE) || target.getId().equals(ctx.userId())) {
            return false;
        }
        for (Long teamId : teams.teamIdsForUser(ctx.tenantId(), ctx.userId())) {
            if (members.activeUserIds(ctx.tenantId(), teamId).contains(target.getId())) {
                return true;
            }
        }
        return false;
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("The date range is invalid");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("Choose a range of " + MAX_RANGE_DAYS + " days or less");
        }
    }

    private static List<LeaveRecord> approved(List<LeaveRecord> all) {
        return all.stream().filter(l -> "APPROVED".equals(l.getStatus())).toList();
    }

    private static boolean isOnLeave(List<LeaveRecord> approved, LocalDate day) {
        for (LeaveRecord l : approved) {
            if (!day.isBefore(l.getStartDate()) && !day.isAfter(l.getEndDate())) {
                return true;
            }
        }
        return false;
    }

    /** Working days of one leave that fall between a and b (inclusive). */
    private static int countDays(LeaveRecord l, LocalDate a, LocalDate b) {
        LocalDate s = l.getStartDate().isAfter(a) ? l.getStartDate() : a;
        LocalDate e = l.getEndDate().isBefore(b) ? l.getEndDate() : b;
        return e.isBefore(s) ? 0 : workingDays(s, e);
    }

    private static int workingDays(LocalDate start, LocalDate end) {
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != WEEKLY_OFF) {
                count++;
            }
        }
        return count;
    }

    private static String topRole(Set<String> roles) {
        for (String r : ROLE_ORDER) {
            if (roles.contains(r)) {
                return r;
            }
        }
        return "EMPLOYEE";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
