package com.antiam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 应用内权限点，由应用在注册或同步时声明，编码在应用内唯一；reserved 表示系统为委派管理保留的权限点。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "application_permissions")
public class ApplicationPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    private String code;
    private String name;
    private String description;
    private boolean reserved;

    public ApplicationPermission(Application application, String code, String name, String description) {
        this(application, code, name, description, false);
    }

    public ApplicationPermission(Application application, String code, String name, String description, boolean reserved) {
        this.application = application;
        this.code = code;
        this.name = name;
        this.description = description;
        this.reserved = reserved;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
