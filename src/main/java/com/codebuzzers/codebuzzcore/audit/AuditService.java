package com.codebuzzers.codebuzzcore.audit;

import com.codebuzzers.codebuzzcore.common.TenantContext;
import com.codebuzzers.codebuzzcore.common.WebRequests;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(Long tenantId, Long actorUserId, String action, String entityType, String entityId,
                       String beforeJson, String afterJson, String source) {
        AuditLog log = new AuditLog();
        TenantContext.Ctx ctx = TenantContext.get();
        log.setTenantId(tenantId);
        log.setActorUserId(actorUserId != null ? actorUserId : ctx == null ? null : ctx.actorUserId());
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setBeforeJson(beforeJson);
        log.setAfterJson(afterJson);
        log.setSource(source);
        log.setIpHash(WebRequests.ipHash());
        log.setRequestId(WebRequests.requestId());
        log.setCreatedAt(Instant.now());
        repository.save(log);
    }
}
