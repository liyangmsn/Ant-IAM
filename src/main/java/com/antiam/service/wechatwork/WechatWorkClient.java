package com.antiam.service.wechatwork;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 企业微信服务端 API 客户端：缓存 access_token，并在 token 失效时自动刷新重试一次。
 */
@Component
public class WechatWorkClient {

    public static final String DEFAULT_ENDPOINT = "https://qyapi.weixin.qq.com";

    private static final Set<Integer> TOKEN_INVALID_CODES = Set.of(40014, 42001, 42007, 42009);
    private static final long EXPIRY_SAFETY_SECONDS = 300;

    private final ObjectMapper objectMapper;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public WechatWorkClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String endpoint, String corpId, String secret, String path, Map<String, ?> query) {
        return call(endpoint, corpId, secret, accessToken -> RestClient.create()
            .get()
            .uri(uri(endpoint, path, accessToken, query))
            .retrieve()
            .body(String.class));
    }

    public JsonNode post(String endpoint, String corpId, String secret, String path, Object body) {
        return call(endpoint, corpId, secret, accessToken -> RestClient.create()
            .post()
            .uri(uri(endpoint, path, accessToken, Map.of()))
            .body(body)
            .retrieve()
            .body(String.class));
    }

    public static boolean isSuccess(JsonNode response) {
        return response != null && response.path("errcode").asInt(0) == 0;
    }

    public static String errorMessage(JsonNode response) {
        if (response == null) {
            return "empty response";
        }
        return response.path("errcode").asInt() + " " + response.path("errmsg").asText("unknown error");
    }

    private JsonNode call(String endpoint, String corpId, String secret, java.util.function.Function<String, String> request) {
        String cacheKey = cacheKey(endpoint, corpId, secret);
        JsonNode response = parse(request.apply(accessToken(endpoint, corpId, secret, cacheKey)));
        if (response != null && TOKEN_INVALID_CODES.contains(response.path("errcode").asInt(0))) {
            tokens.remove(cacheKey);
            response = parse(request.apply(accessToken(endpoint, corpId, secret, cacheKey)));
        }
        return response;
    }

    private String accessToken(String endpoint, String corpId, String secret, String cacheKey) {
        CachedToken cached = tokens.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        String body = RestClient.create()
            .get()
            .uri(UriComponentsBuilder.fromUriString(endpoint)
                .path("/cgi-bin/gettoken")
                .queryParam("corpid", "{corpId}")
                .queryParam("corpsecret", "{secret}")
                .buildAndExpand(corpId, secret)
                .encode()
                .toUri())
            .retrieve()
            .body(String.class);
        JsonNode response = parse(body);
        if (!isSuccess(response) || response.path("access_token").asText().isBlank()) {
            throw new IllegalStateException("WeCom gettoken request failed: " + errorMessage(response));
        }
        long expiresIn = Math.max(60, response.path("expires_in").asLong(7200) - EXPIRY_SAFETY_SECONDS);
        CachedToken token = new CachedToken(response.path("access_token").asText(), Instant.now().plusSeconds(expiresIn));
        tokens.put(cacheKey, token);
        return token.value();
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("WeCom response is not valid JSON", ex);
        }
    }

    private java.net.URI uri(String endpoint, String path, String accessToken, Map<String, ?> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpoint)
            .path(path)
            .queryParam("access_token", "{access_token}");
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("access_token", accessToken);
        query.forEach((key, value) -> {
            builder.queryParam(key, "{" + key + "}");
            variables.put(key, value);
        });
        return builder.buildAndExpand(variables).encode().toUri();
    }

    private String cacheKey(String endpoint, String corpId, String secret) {
        return endpoint + "|" + corpId + "|" + Integer.toHexString(secret.hashCode());
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
