package com.codebuzzers.codebuzzcore.meta;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class WebhookSignatureVerifier {

    public boolean isValid(String rawPayload, String signatureHeader, String appSecret) {
        if (rawPayload == null || signatureHeader == null || appSecret == null || appSecret.isBlank()) {
            return false;
        }
        if (!signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String expected = hmac(rawPayload, appSecret);
        String provided = signatureHeader.substring("sha256=".length());
        return expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
