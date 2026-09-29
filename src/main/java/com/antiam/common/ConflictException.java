package com.antiam.common;

/**
 * 资源已存在或与现有数据冲突。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
