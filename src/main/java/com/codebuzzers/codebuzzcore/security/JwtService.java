package com.codebuzzers.codebuzzcore.security;

import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.Hashes;
import com.codebuzzers.codebuzzcore.common.SecretMaterial;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtService {

    private final SecretMaterial secrets;

    public JwtService(SecretMaterial secrets) {
        this.secrets = secrets;
    }

    public String accessToken(String userPublicId, String viewTenantPublicId, Duration ttl) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userPublicId);
        claims.put("typ", "access");
        if (viewTenantPublicId != null) {
            claims.put("viewTenant", viewTenantPublicId);
        }
        Instant now = Instant.now();
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(ttl).getEpochSecond());
        return sign(claims);
    }

    public Map<String, Object> parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw ApiException.unauthorized("Access token is invalid");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = hmac(signingInput);
        if (!constantTime(expected, parts[2])) {
            throw ApiException.unauthorized("Access token is invalid");
        }
        Map<String, Object> claims = decodeJson(parts[1]);
        Object exp = claims.get("exp");
        long expiry = exp instanceof Number number ? number.longValue() : 0L;
        if (expiry < Instant.now().getEpochSecond()) {
            throw ApiException.unauthorized("Access token has expired");
        }
        return claims;
    }

    private String sign(Map<String, Object> claims) {
        String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String body = base64(toJson(claims));
        String signingInput = header + "." + body;
        return signingInput + "." + hmac(signingInput);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secrets.jwtSecret(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    private static boolean constantTime(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String toJson(Map<String, Object> claims) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append('"').append(String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static Map<String, Object> decodeJson(String part) {
        String json = new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
        Map<String, Object> claims = new LinkedHashMap<>();
        String body = json.trim();
        if (body.length() < 2 || body.charAt(0) != '{') {
            throw ApiException.unauthorized("Access token is invalid");
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return claims;
        }
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().replace("\"", "");
            String raw = kv[1].trim();
            if (raw.startsWith("\"")) {
                claims.put(key, raw.substring(1, raw.length() - 1));
            } else {
                claims.put(key, Long.parseLong(raw));
            }
        }
        return claims;
    }

    public String hash(String token) {
        return Hashes.sha256(token);
    }
}
