package com.antiam.common;

/**
 * 当前主体无权操作目标资源。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
