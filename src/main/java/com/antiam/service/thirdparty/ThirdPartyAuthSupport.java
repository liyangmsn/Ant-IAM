package com.antiam.service.thirdparty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import org.springframework.web.util.UriComponentsBuilder;

public final class ThirdPartyAuthSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    /**
     * 解析回调地址：调用方传入的地址必须是 http(s) 绝对地址，且与配置的 redirectUri 同源或位于 allowedRedirectUris 白名单中。
     */
    public static String redirectUri(JsonNode configuration, String redirectUri) {
        String configured = text(configuration, "redirectUri");
        if (redirectUri == null || redirectUri.isBlank()) {
            return required(configuration, "redirectUri");
        }
        String requested = redirectUri.trim();
        URI requestedUri = httpUri(requested);
        if (requestedUri == null) {
            throw new IllegalArgumentException("Third-party login redirectUri must be an absolute http(s) URL");
        }
        if (requested.equals(configured) || allowedRedirectUris(configuration).contains(requested)) {
            return requested;
        }
        URI configuredUri = configured == null ? null : httpUri(configured);
        if (configuredUri != null && sameOrigin(configuredUri, requestedUri)) {
            return requested;
        }
        throw new IllegalArgumentException("Third-party login redirectUri is not allowed: " + requested);
    }

    private static java.util.Set<String> allowedRedirectUris(JsonNode configuration) {
        java.util.Set<String> values = new java.util.HashSet<>();
        JsonNode node = configuration == null ? null : configuration.path("allowedRedirectUris");
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) {
                    values.add(item.asText().trim());
                }
            });
        }
        return values;
    }

    private static URI httpUri(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null && uri.getFragment() == null ? uri : null;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
            && left.getHost().equalsIgnoreCase(right.getHost())
            && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * 按字符串读取上游响应再解析，兼容 text/plain 等非 JSON Content-Type。
     */
    public static JsonNode readJson(String body, String operation) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException(operation + " request failed: empty response");
        }
        try {
            return JSON.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException(operation + " request failed: invalid JSON response", ex);
        }
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
