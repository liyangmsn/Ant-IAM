package com.antiam.domain;

/**
 * 受限会话类型：登录成功但必须先完成指定操作，期间只能访问自助账号接口。
 */
public enum SessionRestriction {
    PASSWORD_CHANGE,
    MFA_ENROLLMENT
}
