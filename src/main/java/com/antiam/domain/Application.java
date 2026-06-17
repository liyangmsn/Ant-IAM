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
@Table(name = "applications")
public class Application extends BaseEntity {

    private String code;
    private String name;
    private String description;
    private String loginUrl;
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    private ApplicationProtocol protocol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ApplicationGroup group;

    @ManyToMany
    @JoinTable(
        name = "application_roles",
        joinColumns = @JoinColumn(name = "application_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    public Application(String code, String name, ApplicationProtocol protocol, String loginUrl, String description, Tenant tenant, ApplicationGroup group) {
        this.code = code;
        this.name = name;
        this.protocol = protocol;
        this.loginUrl = loginUrl;
        this.description = description;
        this.tenant = tenant;
        this.group = group;
        this.enabled = true;
    }

    public void update(String name, ApplicationProtocol protocol, String loginUrl, String description, ApplicationGroup group) {
        this.name = name;
        this.protocol = protocol;
        this.loginUrl = loginUrl;
        this.description = description;
        this.group = group;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void grant(Role role) {
        roles.add(role);
    }

    public void revoke(Role role) {
        roles.remove(role);
    }
}
