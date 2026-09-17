package com.antiam.service.storage;

import com.fasterxml.jackson.databind.JsonNode;

final class StorageConfig {

    private StorageConfig() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    static String required(JsonNode node, String provider, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException(provider + " storage field is required: " + field);
        }
        return value;
    }

    static String publicUrl(JsonNode node, String provider) {
        return stripTrailingSlash(required(node, provider, "publicUrl"));
    }

    static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    static String hostOf(String endpoint) {
        int separator = endpoint.indexOf("://");
        return separator < 0 ? endpoint : endpoint.substring(separator + 3);
    }
}
