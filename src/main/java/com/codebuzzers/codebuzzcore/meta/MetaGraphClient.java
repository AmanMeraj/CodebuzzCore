package com.codebuzzers.codebuzzcore.meta;

import com.codebuzzers.codebuzzcore.common.AppProperties;
import com.codebuzzers.codebuzzcore.common.Jsons;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MetaGraphClient {

    private final RestClient graph = RestClient.builder().baseUrl("https://graph.facebook.com").build();
    private final ObjectMapper objectMapper;
    private final String version;

    public MetaGraphClient(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.version = properties.meta().graphVersion();
    }

    public JsonNode get(String path, String accessToken, Map<String, String> query) {
        return exchange(path, accessToken, query);
    }

    /** OAuth token exchange. The app secret travels as a query parameter, never as a logged header. */
    public JsonNode getPublic(String path, Map<String, String> query) {
        return exchange(path, null, query);
    }

    private JsonNode exchange(String path, String accessToken, Map<String, String> query) {
        if (path == null || !path.matches("[A-Za-z0-9_./-]{1,180}") || path.contains("..")) {
            throw new IllegalArgumentException("invalid graph path");
        }
        StringBuilder uri = new StringBuilder("/").append(version).append("/").append(path);
        Map<String, Object> vars = new LinkedHashMap<>();
        if (query != null) {
            char separator = '?';
            int index = 0;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!entry.getKey().matches("[A-Za-z0-9_]{1,40}")) {
                    throw new IllegalArgumentException("invalid query");
                }
                String key = "q" + index++;
                uri.append(separator).append(entry.getKey()).append("={").append(key).append("}");
                vars.put(key, entry.getValue());
                separator = '&';
            }
        }
        try {
            var request = graph.get().uri(uri.toString(), vars);
            if (accessToken != null) {
                request = request.header("Authorization", "Bearer " + accessToken);
            }
            String body = request.retrieve().body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public JsonNode postForm(String path, String accessToken, Map<String, String> form) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        try {
            if (path == null || !path.matches("[A-Za-z0-9_./-]{1,180}") || path.contains("..")) {
                throw new IllegalArgumentException("invalid graph path");
            }
            String response = graph.post()
                    .uri("/" + version + "/" + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public String version() {
        return version;
    }

    private MetaApiException translate(RestClientResponseException ex) {
        int code = 0;
        String message = "meta_http_" + ex.getStatusCode().value();
        String detail = null;
        try {
            JsonNode error = objectMapper.readTree(ex.getResponseBodyAsString()).path("error");
            String codeText = Jsons.text(error.path("code"));
            if (codeText != null) {
                code = Integer.parseInt(codeText);
            } else if (error.path("code").isNumber()) {
                code = error.path("code").asInt();
            }
            message = "meta_" + (code == 0 ? ex.getStatusCode().value() : code);
            String text = Jsons.text(error.path("message"));
            String sub = Jsons.text(error.path("error_subcode"));
            detail = (text == null ? "" : text) + (sub == null ? "" : " (subcode " + sub + ")");
        } catch (Exception ignored) {
            message = "meta_http_" + ex.getStatusCode().value();
        }
        return new MetaApiException(ex.getStatusCode().value(), code, message, detail);
    }
}