package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "authentication_providers")
public class AuthenticationProvider extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String providerKey;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private AuthenticationProviderKind provider;

    @Enumerated(EnumType.STRING)
    private AuthenticationProviderType type;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String configuration;

    private boolean visible;
    private boolean enabled;

    public AuthenticationProvider(
        String providerKey,
        String name,
        AuthenticationProviderKind provider,
        AuthenticationProviderType type,
        String description,
        String configuration,
        boolean visible,
        boolean enabled
    ) {
        this.providerKey = providerKey;
        this.name = name;
        this.provider = provider;
        this.type = type;
        this.description = description;
        this.configuration = configuration;
        this.visible = visible;
        this.enabled = enabled;
    }

    public void update(
        String name,
        AuthenticationProviderKind provider,
        AuthenticationProviderType type,
        String description,
        String configuration,
        boolean visible
    ) {
        this.name = name;
        this.provider = provider;
        this.type = type;
        this.description = description;
        this.configuration = configuration;
        this.visible = visible;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
