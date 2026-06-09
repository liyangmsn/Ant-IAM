package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "identity_source_connectors")
public class IdentitySourceConnector extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_source_id", nullable = false)
    private IdentitySource identitySource;

    @Column(columnDefinition = "text")
    private String configuration;

    @Column(columnDefinition = "text")
    private String secretRef;

    private boolean enabled;

    public IdentitySourceConnector(IdentitySource identitySource, String configuration, String secretRef) {
        this.identitySource = identitySource;
        this.configuration = configuration;
        this.secretRef = secretRef;
        this.enabled = true;
    }

    public void replace(String configuration, String secretRef) {
        this.configuration = configuration;
        this.secretRef = secretRef;
        this.enabled = true;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
