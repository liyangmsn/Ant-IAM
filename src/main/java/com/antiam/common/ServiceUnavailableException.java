package com.antiam.common;

/**
 * 依赖的服务（邮件、存储、GeoIP 等）未配置或暂不可用。
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
