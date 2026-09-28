package com.antiam.domain;

/**
 * 应用授权范围：手动授权时仅授权主体可访问，全员可访问时租户内所有正常用户均可访问。
 */
public enum ApplicationAuthorizationType {
    MANUAL,
    ALL_ACCESS
}
