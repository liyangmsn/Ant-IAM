package com.antiam.common;

/**
 * SCIM 协议错误：携带 HTTP 状态码与 RFC 7644 §3.12 的 scimType，由 SCIM 端点的异常处理器转为 SCIM Error 响应。
 */
public class ScimException extends RuntimeException {

    private final int status;
    private final String scimType;

    public ScimException(int status, String scimType, String detail) {
        super(detail);
        this.status = status;
        this.scimType = scimType;
    }

    public static ScimException unauthorized(String detail) {
        return new ScimException(401, null, detail);
    }

    public static ScimException invalidValue(String detail) {
        return new ScimException(400, "invalidValue", detail);
    }

    public static ScimException mutability(String detail) {
        return new ScimException(400, "mutability", detail);
    }

    public static ScimException uniqueness(String detail) {
        return new ScimException(409, "uniqueness", detail);
    }

    public int status() {
        return status;
    }

    public String scimType() {
        return scimType;
    }
}
