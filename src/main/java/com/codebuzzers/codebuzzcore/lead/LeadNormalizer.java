package com.codebuzzers.codebuzzcore.lead;

import com.codebuzzers.codebuzzcore.common.Jsons;
import com.codebuzzers.codebuzzcore.common.PhoneNormalizer;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class LeadNormalizer {

    private static final Pattern REPEATED_DIGIT = Pattern.compile("(\\d)\\1{6,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Question keys that mean the business, not the person. Used only after every person-name key failed. */
    private static final String[] BUSINESS_WORDS = {"company", "business", "shop", "store", "brand", "firm"};

    private LeadNormalizer() {
    }

    public record Result(String name, String phone, String email, boolean test, boolean spam) {
    }

    public static Result normalize(JsonNode lead, String countryCode) {
        Map<String, List<String>> answers = answers(lead);
        String name = fullName(answers);
        String email = PhoneNormalizer.email(first(answers, "email"));
        String phone = PhoneNormalizer.normalize(first(answers, "phone_number"), countryCode);
        // Forms often use their own question keys (your_name, mobile_number, whatsapp, ...): match loosely.
        if (name == null) {
            // Person name keys first. Business keys (business_name, shop_name, ...) are skipped here...
            name = looseValue(answers, "name", BUSINESS_WORDS);
        }
        if (name == null) {
            name = looseValue(answers, "naam", BUSINESS_WORDS);
        }
        if (name == null) {
            for (String key : new String[]{"owner", "customer", "person"}) {
                name = looseValue(answers, key);
                if (name != null) {
                    break;
                }
            }
        }
        if (name == null) {
            // ...and used here as the fallback, so a form that only asks for a business name still gets a name.
            for (String key : BUSINESS_WORDS) {
                name = looseValue(answers, key);
                if (name != null) {
                    break;
                }
            }
        }
        if (email == null) {
            email = PhoneNormalizer.email(looseValue(answers, "email"));
        }
        if (phone == null) {
            for (String key : new String[]{"phone", "mobile", "whatsapp", "contact"}) {
                phone = PhoneNormalizer.normalize(looseValue(answers, key), countryCode);
                if (phone != null) {
                    break;
                }
            }
        }
        boolean test = truthy(Jsons.text(lead.path("is_test"))) || truthy(first(answers, "is_test"));
        // Spam = a Meta lead with no answers at all, or a fake looking phone. A lead whose answers exist
        // but use unknown keys is NOT spam; it stays NEW so nobody loses a real lead.
        boolean noAnswers = answers.values().stream().allMatch(List::isEmpty);
        boolean spam = (name == null && email == null && phone == null && noAnswers)
                || (phone != null && REPEATED_DIGIT.matcher(phone).find());
        return new Result(name, phone, email, test, spam);
    }

    /** Question keys are lower-cased, trimmed and have whitespace turned into '_' so "Full Name" matches "full_name". */
    public static Map<String, List<String>> answers(JsonNode lead) {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        for (JsonNode field : lead.path("field_data")) {
            String name = Jsons.text(field.path("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (JsonNode value : field.path("values")) {
                String text = Jsons.text(value);
                if (text != null) {
                    values.add(text);
                }
            }
            String key = WHITESPACE.matcher(name.trim().toLowerCase()).replaceAll("_");
            List<String> existing = answers.get(key);
            if (existing == null || existing.isEmpty()) {
                answers.put(key, values);
            }
        }
        return answers;
    }

    /** First non-blank answer whose question key contains the fragment (and none of the excluded words). */
    private static String looseValue(Map<String, List<String>> answers, String fragment, String... excluded) {
        for (Map.Entry<String, List<String>> entry : answers.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (!key.contains(fragment) || entry.getValue().isEmpty()) {
                continue;
            }
            boolean skip = false;
            for (String word : excluded) {
                if (key.contains(word)) {
                    skip = true;
                    break;
                }
            }
            String value = entry.getValue().get(0);
            if (!skip && value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String first(Map<String, List<String>> answers, String key) {
        List<String> values = answers.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String fullName(Map<String, List<String>> answers) {
        String full = first(answers, "full_name");
        if (full != null && !full.isBlank()) {
            return full.trim();
        }
        String combined = ((first(answers, "first_name") == null ? "" : first(answers, "first_name"))
                + " "
                + (first(answers, "last_name") == null ? "" : first(answers, "last_name"))).trim();
        return combined.isEmpty() ? null : combined;
    }

    private static boolean truthy(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes"));
    }
}