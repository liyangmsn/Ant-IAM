package com.antiam.common;

/**
 * 用户未获得应用访问授权时拒绝 SSO 登录。
 */
public class ApplicationAccessDeniedException extends RuntimeException {

    private final String reason;

    public ApplicationAccessDeniedException(String applicationCode, String reason) {
        super("User is not authorized to access application: " + applicationCode);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
