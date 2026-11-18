package com.codebuzzers.codebuzzcore.lead;

import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.identity.TeamRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LeadAccess {

    private final TeamRepository teams;

    public LeadAccess(TeamRepository teams) {
        this.teams = teams;
    }

    public Scope requireRead() {
        TenantContext.Ctx ctx = TenantContext.require();
        if (ctx.has(Codes.Permission.LEAD_VIEW_COMPANY)) {
            return new Scope(ctx, ScopeKind.COMPANY, List.of());
        }
        if (ctx.has(Codes.Permission.LEAD_VIEW_TEAM)) {
            return new Scope(ctx, ScopeKind.TEAM, teams.teamIdsForUser(ctx.tenantId(), ctx.userId()));
        }
        if (ctx.has(Codes.Permission.LEAD_VIEW_OWN)) {
            return new Scope(ctx, ScopeKind.OWN, List.of());
        }
        throw ApiException.forbidden("You cannot view leads");
    }

    public Scope requireUpdate() {
        Scope scope = requireRead();
        if (!scope.ctx().has(Codes.Permission.LEAD_UPDATE)) {
            throw ApiException.forbidden("You cannot update leads");
        }
        return scope;
    }

    public void ensureVisible(Scope scope, Lead lead) {
        if (lead == null || !lead.getTenantId().equals(scope.ctx().tenantId()) || !visible(scope, lead)) {
            throw ApiException.notFound("Lead not found");
        }
    }

    public boolean visible(Scope scope, Lead lead) {
        return switch (scope.kind()) {
            case COMPANY -> true;
            case OWN -> scope.ctx().userId() == (lead.getAssignedUserId() == null ? -1 : lead.getAssignedUserId());
            case TEAM -> scope.ctx().userId() == (lead.getAssignedUserId() == null ? -1 : lead.getAssignedUserId())
                    || (lead.getAssignedTeamId() != null && scope.teamIds().contains(lead.getAssignedTeamId()));
        };
    }

    public enum ScopeKind { COMPANY, TEAM, OWN }

    public record Scope(TenantContext.Ctx ctx, ScopeKind kind, List<Long> teamIds) {
    }
}
