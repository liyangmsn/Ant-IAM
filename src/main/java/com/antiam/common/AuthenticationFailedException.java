package com.antiam.common;

/**
 * 认证失败异常。登录类事务遇到该异常时不回滚，以保留失败计数、锁定状态和失败事件。
 */
public class AuthenticationFailedException extends IllegalArgumentException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
