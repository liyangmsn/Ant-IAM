package com.antiam.domain;

/**
 * 认证事件中记录认证方式的取值：内置认证方式使用固定标识，第三方认证使用认证源 providerKey。
 */
public final class AuthenticationMethods {
    public static final String PASSWORD = "password";
    public static final String MOBILE_CODE = "mobile_code";

    private AuthenticationMethods() {
    }
}
