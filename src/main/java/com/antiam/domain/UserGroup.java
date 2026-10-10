package com.antiam.domain;

import jakarta.persistence.Entity;
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
@Table(name = "user_groups")
public class UserGroup extends BaseEntity {

    private String code;
    private String name;
    private String description;

    /** 推送该用户组的身份源；为空表示本地用户组。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_source_id")
    private IdentitySource identitySource;

    /** 身份源内的用户组 ID，本源内唯一。 */
    private String externalId;

    @ManyToMany
    @JoinTable(
        name = "group_roles",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    public UserGroup(String code, String name) {
        this(code, name, null);
    }

    public UserGroup(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void assignSource(IdentitySource identitySource, String externalId) {
        this.identitySource = identitySource;
        this.externalId = externalId;
    }

    public void grant(Role role) {
        roles.add(role);
    }

    public void revoke(Role role) {
        roles.remove(role);
    }
}
