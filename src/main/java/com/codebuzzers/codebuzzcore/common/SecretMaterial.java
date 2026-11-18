package com.codebuzzers.codebuzzcore.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class SecretMaterial {

    private static final Logger log = LoggerFactory.getLogger(SecretMaterial.class);

    private final byte[] jwtSecret;
    private final SecretKey tokenKey;

    public SecretMaterial(AppProperties properties) {
        this.jwtSecret = material(properties.jwt().secret(), "JWT_SECRET", properties.requireSecrets());
        this.tokenKey = new SecretKeySpec(material(properties.crypto().tokenKey(), "TOKEN_ENCRYPTION_KEY", properties.requireSecrets()), "AES");
    }

    public byte[] jwtSecret() {
        return jwtSecret;
    }

    public SecretKey tokenKey() {
        return tokenKey;
    }

    private static byte[] material(String configured, String name, boolean required) {
        if (configured != null && !configured.isBlank()) {
            byte[] decoded = decode(configured);
            if (decoded.length < 32) {
                return sha256(configured);
            }
            return decoded.length == 32 ? decoded : sha256(configured);
        }
        if (required) {
            throw new IllegalStateException(name + " must be set when app.require-secrets=true");
        }
        log.warn("{} is not set. Using an ephemeral key that will not survive a restart.", name);
        byte[] generated = new byte[32];
        new java.security.SecureRandom().nextBytes(generated);
        return generated;
    }

    private static byte[] decode(String configured) {
        try {
            byte[] decoded = Base64.getDecoder().decode(configured);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Treat the value as a raw secret string.
        }
        return configured.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
