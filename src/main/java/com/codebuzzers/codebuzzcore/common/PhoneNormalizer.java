package com.codebuzzers.codebuzzcore.common;

public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static String normalize(String raw, String countryCode) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }
        if (digits.length() == 10 && "IN".equalsIgnoreCase(countryCode)) {
            return "91" + digits;
        }
        if (digits.startsWith("00")) {
            return digits.substring(2);
        }
        return digits;
    }

    public static String email(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        return value.isEmpty() ? null : value;
    }
}
