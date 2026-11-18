package com.codebuzzers.codebuzzcore;

import com.codebuzzers.codebuzzcore.common.AppProperties;
import com.codebuzzers.codebuzzcore.common.CryptoService;
import com.codebuzzers.codebuzzcore.common.PhoneNormalizer;
import com.codebuzzers.codebuzzcore.common.SecretMaterial;
import com.codebuzzers.codebuzzcore.lead.LeadNormalizer;
import com.codebuzzers.codebuzzcore.lead.RoundRobinPicker;
import com.codebuzzers.codebuzzcore.meta.WebhookSignatureVerifier;
import com.codebuzzers.codebuzzcore.security.JwtService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreRulesTest {

    @Test
    void roundRobinAdvancesAndWraps() {
        assertEquals(10L, RoundRobinPicker.next(List.of(10L, 20L, 30L), null));
        assertEquals(20L, RoundRobinPicker.next(List.of(10L, 20L, 30L), 10L));
        assertEquals(10L, RoundRobinPicker.next(List.of(10L, 20L, 30L), 30L));
        assertNull(RoundRobinPicker.next(List.of(), 10L));
    }

    @Test
    void phoneAndEmailAreNormalized() {
        assertEquals("919876543210", PhoneNormalizer.normalize("98765 43210", "IN"));
        assertEquals("person@example.com", PhoneNormalizer.email(" Person@Example.com "));
    }

    @Test
    void repeatedDigitsAreSpamAndExplicitFlagIsTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var spam = LeadNormalizer.normalize(mapper.readTree("""
                {"field_data":[{"name":"phone_number","values":["9999999999"]}]}
                """), "IN");
        assertTrue(spam.spam());
        var test = LeadNormalizer.normalize(mapper.readTree("""
                {"is_test":"true","field_data":[{"name":"full_name","values":["Asha"]},{"name":"phone_number","values":["9876543210"]}]}
                """), "IN");
        assertTrue(test.test());
        assertFalse(test.spam());
        assertEquals("Asha", test.name());
    }

    @Test
    void signatureRejectsTampering() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier();
        String body = "{\"object\":\"page\"}";
        String secret = "app-secret";
        String signature = "sha256=" + hmac(body, secret);
        assertTrue(verifier.isValid(body, signature, secret));
        assertFalse(verifier.isValid(body + " ", signature, secret));
        assertFalse(verifier.isValid(body, signature, ""));
    }

    @Test
    void tokenRoundTripNeverReturnsPlaintextFromCipher() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("jwt-secret-jwt-secret-jwt-secret-32", Duration.ofMinutes(15), Duration.ofDays(30)),
                new AppProperties.Crypto(Base64.getEncoder().encodeToString(key)),
                new AppProperties.Meta("", "", "", "v26.0", "http://localhost/callback", ""),
                new AppProperties.Fcm(""),
                new AppProperties.Dev(false),
                new AppProperties.Worker(2000, 8, 8),
                true);
        CryptoService crypto = new CryptoService(new SecretMaterial(properties));
        String encrypted = crypto.encrypt("page-token");
        assertFalse(encrypted.contains("page-token"));
        assertEquals("page-token", crypto.decrypt(encrypted));

        JwtService jwt = new JwtService(new SecretMaterial(properties));
        String token = jwt.accessToken("user-1", null, Duration.ofMinutes(5));
        assertEquals("user-1", jwt.parse(token).get("sub"));
        assertEquals("access", jwt.parse(token).get("typ"));
    }

    private static String hmac(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
