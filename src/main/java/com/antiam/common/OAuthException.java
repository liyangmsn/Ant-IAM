package com.antiam.common;

import org.springframework.http.HttpStatus;

/**
 * OAuth2/OIDC 协议错误，按 RFC 6749 §5.2 输出 error 与 error_description。
 */
public class OAuthException extends IllegalArgumentException {

    private final String error;
    private final HttpStatus status;

    public OAuthException(String error, String description) {
        this(error, description, HttpStatus.BAD_REQUEST);
    }

    public OAuthException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OAuthException invalidClient(String description) {
        return new OAuthException("invalid_client", description, HttpStatus.UNAUTHORIZED);
    }

    public static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description);
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description);
    }

    public static OAuthException invalidToken(String description) {
        return new OAuthException("invalid_token", description, HttpStatus.UNAUTHORIZED);
    }

    public String error() {
        return error;
    }

    public HttpStatus status() {
        return status;
    }
}
