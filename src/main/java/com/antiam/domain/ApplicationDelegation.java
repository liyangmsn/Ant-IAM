package com.antiam.domain;

/**
 * 应用权限委派使用的系统保留编码。保留权限点与内置角色随应用自动创建，编码以 {@code iam:} 开头，
 * 应用自己声明的权限点和角色不能使用这个前缀。
 */
public final class ApplicationDelegation {

    public static final String RESERVED_PREFIX = "iam:";
    public static final String PERMISSION_MANAGE = "iam:app:permission:manage";
    public static final String GRANT_MANAGE = "iam:app:grant:manage";
    public static final String OWNER_ROLE = "iam:app-owner";
    public static final String GRANT_MANAGER_ROLE = "iam:app-grant-manager";

    private ApplicationDelegation() {
    }

    public static boolean isReservedCode(String code) {
        return code != null && code.startsWith(RESERVED_PREFIX);
    }
}
