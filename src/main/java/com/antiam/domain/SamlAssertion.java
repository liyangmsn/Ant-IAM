package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "saml_assertions")
public class SamlAssertion extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String assertionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    private String issuer;
    private String audience;
    private String acsUrl;
    private Instant notBefore;
    private Instant notOnOrAfter;

    @Column(columnDefinition = "text")
    private String attributes;

    public SamlAssertion(
        String assertionId,
        Application application,
        UserAccount user,
        String issuer,
        String audience,
        String acsUrl,
        Instant notBefore,
        Instant notOnOrAfter,
        String attributes
    ) {
        this.assertionId = assertionId;
        this.application = application;
        this.user = user;
        this.issuer = issuer;
        this.audience = audience;
        this.acsUrl = acsUrl;
        this.notBefore = notBefore;
        this.notOnOrAfter = notOnOrAfter;
        this.attributes = attributes;
    }
}
