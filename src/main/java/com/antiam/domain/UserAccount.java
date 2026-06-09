package com.antiam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_accounts")
public class UserAccount extends BaseEntity {

    private String username;
    private String displayName;
    private String email;
    private String mobile;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToMany
    @JoinTable(
        name = "user_group_members",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<UserGroup> groups = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    public UserAccount(
        String username,
        String displayName,
        String email,
        String mobile,
        Tenant tenant,
        Organization organization
    ) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.mobile = mobile;
        this.tenant = tenant;
        this.organization = organization;
        this.status = AccountStatus.ACTIVE;
    }

    public void join(UserGroup group) {
        groups.add(group);
    }

    public void leave(UserGroup group) {
        groups.remove(group);
    }

    public void grant(Role role) {
        roles.add(role);
    }

    public void revoke(Role role) {
        roles.remove(role);
    }

    public void updateProfile(String displayName, String email, String mobile, Organization organization) {
        this.displayName = displayName;
        this.email = email;
        this.mobile = mobile;
        this.organization = organization;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }

    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
    }

    public void lock() {
        this.status = AccountStatus.LOCKED;
    }

    public void depart() {
        this.status = AccountStatus.DEPARTED;
    }
}
