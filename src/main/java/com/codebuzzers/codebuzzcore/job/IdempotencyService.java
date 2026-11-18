package com.codebuzzers.codebuzzcore.job;

import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.WebRequests;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final IdempotencyKeyRepository keys;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper objectMapper) {
        this.keys = keys;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> once(long tenantId, String operation, Supplier<Map<String, Object>> action) {
        String key = WebRequests.idempotencyKey();
        if (key == null) {
            return action.get();
        }
        if (key.length() > 128) {
            throw ApiException.badRequest("Idempotency-Key is too long");
        }
        var existing = keys.findByTenantIdAndIdempotencyKey(tenantId, key);
        if (existing.isPresent() && existing.get().getResponseJson() != null) {
            return objectMapper.readValue(existing.get().getResponseJson(), MAP);
        }
        Map<String, Object> result = action.get();
        IdempotencyKey row = existing.orElseGet(IdempotencyKey::new);
        row.setTenantId(tenantId);
        row.setIdempotencyKey(key);
        row.setOperation(operation);
        row.setResponseJson(objectMapper.writeValueAsString(result));
        row.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(Instant.now());
        }
        try {
            keys.save(row);
        } catch (DataIntegrityViolationException ex) {
            return keys.findByTenantIdAndIdempotencyKey(tenantId, key)
                    .filter(saved -> saved.getResponseJson() != null)
                    .map(saved -> objectMapper.readValue(saved.getResponseJson(), MAP))
                    .orElse(result);
        }
        return result;
    }
}
