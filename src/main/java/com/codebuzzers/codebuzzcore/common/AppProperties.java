package com.codebuzzers.codebuzzcore.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Crypto crypto,
        Meta meta,
        Fcm fcm,
        Dev dev,
        Worker worker,
        boolean requireSecrets
) {
    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
    }

    public record Crypto(String tokenKey) {
    }

    public record Meta(
            String appId,
            String appSecret,
            String webhookVerifyToken,
            String graphVersion,
            String oauthRedirectUri,
            String successRedirect,
            String configId
    ) {
    }

    public record Fcm(String serverKey) {
    }

    public record Dev(boolean exposeResetToken) {
    }

    public record Worker(long pollDelayMs, int batchSize, int maxAttempts) {
    }
}