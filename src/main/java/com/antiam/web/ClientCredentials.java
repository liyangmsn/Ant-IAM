package com.antiam.web;

import com.antiam.common.OAuthException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OAuth 客户端凭据：Basic 头（RFC 6749 §2.3.1，id/secret 先做 form 编码）与表单参数二选一，不允许同时携带密钥。
 */
record ClientCredentials(String id, String secret) {

    static ClientCredentials parse(String authorization, String clientId, String clientSecret) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, "Basic ".length())) {
            return new ClientCredentials(clientId, clientSecret);
        }
        if (clientSecret != null && !clientSecret.isBlank()) {
            throw OAuthException.invalidRequest("Client authentication must use only one method");
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring("Basic ".length()).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw OAuthException.invalidClient("Malformed Basic client credentials");
        }
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            throw OAuthException.invalidClient("Malformed Basic client credentials");
        }
        String basicId = URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8);
        String basicSecret = URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8);
        if (clientId != null && !clientId.isBlank() && !clientId.equals(basicId)) {
            throw OAuthException.invalidRequest("client_id does not match Basic client credentials");
        }
        return new ClientCredentials(basicId, basicSecret);
    }
}
