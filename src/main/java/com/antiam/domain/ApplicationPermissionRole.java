package com.antiam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 应用内角色：聚合同一应用的权限点，再授予用户、用户组或组织；builtIn 表示系统内置、不可修改的委派管理角色。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "application_permission_roles")
public class ApplicationPermissionRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    private String code;
    private String name;
    private String description;
    private boolean builtIn;

    @ManyToMany
    @JoinTable(
        name = "application_permission_role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<ApplicationPermission> permissions = new LinkedHashSet<>();

    public ApplicationPermissionRole(Application application, String code, String name, String description) {
        this(application, code, name, description, false);
    }

    public ApplicationPermissionRole(Application application, String code, String name, String description, boolean builtIn) {
        this.application = application;
        this.code = code;
        this.name = name;
        this.description = description;
        this.builtIn = builtIn;
    }

    /** 是否包含委派管理用的保留权限点；包含时只有应用权限负责人或 IAM 管理员能授予。 */
    public boolean grantsDelegation() {
        return permissions.stream().anyMatch(ApplicationPermission::isReserved);
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replacePermissions(Collection<ApplicationPermission> permissions) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    public void revoke(ApplicationPermission permission) {
        permissions.remove(permission);
    }
}
