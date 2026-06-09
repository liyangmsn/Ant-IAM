package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "application_sso_configs")
public class ApplicationSsoConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    private ApplicationProtocol protocol;

    private String clientId;

    @Column(columnDefinition = "text")
    private String clientSecretHash;

    @Column(columnDefinition = "text")
    private String redirectUris;

    @Column(columnDefinition = "text")
    private String scopes;

    private String samlEntityId;
    private String samlAcsUrl;
    private String casServiceUrl;
    private String jwtAudience;

    @Column(columnDefinition = "text")
    private String formLoginTemplate;

    @Column(columnDefinition = "text")
    private String idTokenClaims;

    @Column(columnDefinition = "text")
    private String customClaims;

    private boolean enabled;

    public ApplicationSsoConfig(
        Application application,
        ApplicationProtocol protocol,
        String clientId,
        String clientSecretHash,
        String redirectUris,
        String scopes,
        String samlEntityId,
        String samlAcsUrl,
        String casServiceUrl,
        String jwtAudience,
        String formLoginTemplate,
        String idTokenClaims,
        String customClaims
    ) {
        this.application = application;
        this.protocol = protocol;
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.redirectUris = redirectUris;
        this.scopes = scopes;
        this.samlEntityId = samlEntityId;
        this.samlAcsUrl = samlAcsUrl;
        this.casServiceUrl = casServiceUrl;
        this.jwtAudience = jwtAudience;
        this.formLoginTemplate = formLoginTemplate;
        this.idTokenClaims = idTokenClaims;
        this.customClaims = customClaims;
        this.enabled = true;
    }

    public void replaceWith(ApplicationSsoConfig replacement) {
        this.protocol = replacement.protocol;
        this.clientId = replacement.clientId;
        this.clientSecretHash = replacement.clientSecretHash;
        this.redirectUris = replacement.redirectUris;
        this.scopes = replacement.scopes;
        this.samlEntityId = replacement.samlEntityId;
        this.samlAcsUrl = replacement.samlAcsUrl;
        this.casServiceUrl = replacement.casServiceUrl;
        this.jwtAudience = replacement.jwtAudience;
        this.formLoginTemplate = replacement.formLoginTemplate;
        this.idTokenClaims = replacement.idTokenClaims;
        this.customClaims = replacement.customClaims;
        this.enabled = replacement.enabled;
    }
}
