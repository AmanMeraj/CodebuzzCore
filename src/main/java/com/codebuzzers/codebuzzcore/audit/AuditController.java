package com.codebuzzers.codebuzzcore.audit;

import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.PageResponse;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.security.AccessGuard;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AccessGuard access;
    private final AuditLogRepository logs;

    public AuditController(AccessGuard access, AuditLogRepository logs) {
        this.access = access;
        this.logs = logs;
    }

    @GetMapping
    public PageResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        TenantContext.Ctx ctx = access.require(Codes.Permission.AUDIT_VIEW);
        var result = logs.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.getContent().stream().map(row -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", row.getAction());
            body.put("entityType", row.getEntityType());
            body.put("entityId", row.getEntityId());
            body.put("source", row.getSource());
            body.put("requestId", row.getRequestId());
            body.put("createdAt", row.getCreatedAt());
            return body;
        }).toList(), page, size, result.getTotalElements());
    }
}
