package com.codebuzzers.codebuzzcore.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class WebRequests {

    private WebRequests() {
    }

    public static HttpServletRequest current() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }

    public static String ipHash() {
        HttpServletRequest request = current();
        if (request == null || request.getRemoteAddr() == null) {
            return null;
        }
        return Hashes.sha256(request.getRemoteAddr());
    }

    public static String userAgent() {
        HttpServletRequest request = current();
        if (request == null) {
            return null;
        }
        String agent = request.getHeader("User-Agent");
        if (agent == null) {
            return null;
        }
        return agent.length() > 255 ? agent.substring(0, 255) : agent;
    }

    public static String idempotencyKey() {
        HttpServletRequest request = current();
        if (request == null) {
            return null;
        }
        String key = request.getHeader("Idempotency-Key");
        return key == null || key.isBlank() ? null : key.trim();
    }

    public static String requestId() {
        return MDC.get("requestId");
    }
}
