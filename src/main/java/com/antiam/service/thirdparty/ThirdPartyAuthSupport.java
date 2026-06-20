package com.antiam.service.thirdparty;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.web.util.UriComponentsBuilder;

public final class ThirdPartyAuthSupport {

    private ThirdPartyAuthSupport() {
    }

    public static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Third-party login configuration field is required: " + field);
        }
        return value;
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    public static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return value == null ? defaultValue : value;
    }

    public static boolean boolOrDefault(JsonNode node, String field, boolean defaultValue) {
        return node != null && node.has(field) ? node.path(field).asBoolean(defaultValue) : defaultValue;
    }

    public static String redirectUri(JsonNode configuration, String redirectUri) {
        if (redirectUri != null && !redirectUri.isBlank()) {
            return redirectUri;
        }
        return required(configuration, "redirectUri");
    }

    public static String state(String state) {
        return state == null || state.isBlank() ? UUID.randomUUID().toString() : state;
    }

    public static UriComponentsBuilder uri(String endpoint) {
        return UriComponentsBuilder.fromUriString(endpoint);
    }

    public static java.util.Map<String, Object> raw(Object... pairs) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                values.put(pairs[i].toString(), pairs[i + 1]);
            }
        }
        return values;
    }
}
