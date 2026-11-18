package com.codebuzzers.codebuzzcore.support;

import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.job.BackgroundJobRepository;
import com.codebuzzers.codebuzzcore.meta.MetaConnectionRepository;
import com.codebuzzers.codebuzzcore.security.AccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final AccessGuard access;
    private final SupportTicketRepository tickets;
    private final BackgroundJobRepository jobs;
    private final MetaConnectionRepository connections;

    public SupportController(AccessGuard access, SupportTicketRepository tickets, BackgroundJobRepository jobs,
                             MetaConnectionRepository connections) {
        this.access = access;
        this.tickets = tickets;
        this.jobs = jobs;
        this.connections = connections;
    }

    @GetMapping("/tickets")
    public List<Map<String, Object>> list() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.SUPPORT_VIEW);
        return tickets.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId()).stream().map(this::view).toList();
    }

    @PostMapping("/tickets")
    @Transactional
    public Map<String, Object> create(@Valid @RequestBody TicketRequest request) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.SUPPORT_VIEW);
        SupportTicket ticket = new SupportTicket();
        ticket.setTenantId(ctx.tenantId());
        ticket.setCreatedBy(ctx.userId());
        ticket.setSubject(request.subject().trim());
        ticket.setBody(request.body().trim());
        ticket.setStatus("OPEN");
        tickets.save(ticket);
        return view(ticket);
    }

    @PostMapping("/tickets/{id}/close")
    @Transactional
    public Map<String, Object> close(@PathVariable String id) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.SUPPORT_MANAGE);
        SupportTicket ticket = tickets.findByPublicIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> ApiException.notFound("Ticket not found"));
        ticket.setStatus("CLOSED");
        return view(ticket);
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        TenantContext.Ctx ctx = access.require(Codes.Permission.SUPPORT_MANAGE);
        long unhealthy = connections.findByTenantIdOrderByIdDesc(ctx.tenantId()).stream()
                .filter(connection -> "UNHEALTHY".equals(connection.getStatus())).count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deadJobs", jobs.countByStatus(Codes.JobStatus.DEAD));
        body.put("unhealthyConnections", unhealthy);
        body.put("note", "Counts only. Lead payloads are not included.");
        return body;
    }

    private Map<String, Object> view(SupportTicket ticket) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", ticket.getPublicId());
        body.put("subject", ticket.getSubject());
        body.put("body", ticket.getBody());
        body.put("status", ticket.getStatus());
        body.put("createdAt", ticket.getCreatedAt());
        return body;
    }

    public record TicketRequest(@NotBlank @Size(max = 160) String subject, @NotBlank @Size(max = 4000) String body) {
    }
}
