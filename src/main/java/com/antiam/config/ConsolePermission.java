package com.antiam.config;

import java.util.Arrays;
import java.util.List;

/**
 * 控制台内置权限点。编码统一使用 {@code iam:<模块>:<read|write>}，write 隐含 read。
 */
public enum ConsolePermission {

    DASHBOARD_READ("iam:dashboard:read", "查看概览", "查看控制台概览与统计数据"),
    USER_READ("iam:user:read", "查看用户与组织", "查看用户、组织、用户组及 SCIM 目录"),
    USER_WRITE("iam:user:write", "管理用户与组织", "维护用户、组织、用户组及 SCIM 目录"),
    ROLE_READ("iam:role:read", "查看角色与权限", "查看角色、权限及其授权关系"),
    ROLE_WRITE("iam:role:write", "管理角色与权限", "维护角色、权限及其授权关系，不含控制台权限点"),
    APPLICATION_READ("iam:application:read", "查看应用", "查看应用、应用授权、访问申请、令牌与签名密钥"),
    APPLICATION_WRITE("iam:application:write", "管理应用", "维护应用、应用授权、访问申请、令牌与签名密钥"),
    IDENTITY_SOURCE_READ("iam:identity-source:read", "查看身份源", "查看身份源、连接器与同步任务"),
    IDENTITY_SOURCE_WRITE("iam:identity-source:write", "管理身份源", "维护身份源、连接器并执行同步"),
    AUTHENTICATION_READ("iam:authentication:read", "查看认证", "查看认证源、认证策略、会话与认证事件"),
    AUTHENTICATION_WRITE("iam:authentication:write", "管理认证", "维护认证源、认证策略并结束会话"),
    SECURITY_READ("iam:security:read", "查看安全设置", "查看安全设置与风险规则"),
    SECURITY_WRITE("iam:security:write", "管理安全设置", "维护安全设置与风险规则"),
    SYSTEM_READ("iam:system:read", "查看系统设置", "查看系统参数与租户"),
    SYSTEM_WRITE("iam:system:write", "管理系统设置", "维护系统参数、租户并上传文件"),
    AUDIT_READ("iam:audit:read", "查看审计", "查看并导出审计日志");

    public static final String CODE_PREFIX = "iam:";

    private final String code;
    private final String displayName;
    private final String description;

    ConsolePermission(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static boolean isConsoleCode(String code) {
        return code != null && code.startsWith(CODE_PREFIX);
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(ConsolePermission::code).toList();
    }
}
