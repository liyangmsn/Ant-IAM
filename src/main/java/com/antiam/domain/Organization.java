package com.antiam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    private String code;
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Organization parent;

    /** 推送该组织的身份源；为空表示本地组织。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_source_id")
    private IdentitySource identitySource;

    /** 身份源内的组织 ID，本源内唯一。 */
    private String externalId;

    public Organization(String code, String name, Organization parent) {
        this.code = code;
        this.name = name;
        this.parent = parent;
    }

    public void update(String name, Organization parent) {
        this.name = name;
        this.parent = parent;
    }

    public void assignSource(IdentitySource identitySource, String externalId) {
        this.identitySource = identitySource;
        this.externalId = externalId;
    }
}
