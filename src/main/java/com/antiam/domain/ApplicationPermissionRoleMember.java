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
 * 应用内角色的授予对象，user、group、organization 三者只能填写一个；授予组织即覆盖其下级组织成员。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "application_permission_role_members")
public class ApplicationPermissionRoleMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private ApplicationPermissionRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private UserGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    public static ApplicationPermissionRoleMember ofUser(ApplicationPermissionRole role, UserAccount user) {
        ApplicationPermissionRoleMember member = new ApplicationPermissionRoleMember(role);
        member.user = user;
        return member;
    }

    public static ApplicationPermissionRoleMember ofGroup(ApplicationPermissionRole role, UserGroup group) {
        ApplicationPermissionRoleMember member = new ApplicationPermissionRoleMember(role);
        member.group = group;
        return member;
    }

    public static ApplicationPermissionRoleMember ofOrganization(ApplicationPermissionRole role, Organization organization) {
        ApplicationPermissionRoleMember member = new ApplicationPermissionRoleMember(role);
        member.organization = organization;
        return member;
    }

    private ApplicationPermissionRoleMember(ApplicationPermissionRole role) {
        this.role = role;
    }
}
