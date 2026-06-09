package com.antiam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "identity_sources")
public class IdentitySource extends BaseEntity {

    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    private IdentitySourceType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    private boolean enabled;

    public IdentitySource(String code, String name, IdentitySourceType type, Tenant tenant) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.tenant = tenant;
        this.enabled = true;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
