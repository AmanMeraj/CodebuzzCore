package com.codebuzzers.codebuzzcore.common;

import tools.jackson.databind.JsonNode;

public final class Jsons {

    private Jsons() {
    }

    public static String text(JsonNode node) {
        try {
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            if (node.isValueNode()) {
                String value = node.asString();
                return value == null || value.isBlank() ? null : value;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
