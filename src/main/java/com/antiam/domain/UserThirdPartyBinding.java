package com.antiam.domain;

import jakarta.persistence.Column;
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
@Table(name = "user_third_party_bindings")
public class UserThirdPartyBinding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthenticationProviderKind provider;

    @Column(nullable = false)
    private String subject;

    private String unionId;
    private String displayName;
    private String email;
    private String mobile;
    private String avatarUrl;

    @Column(columnDefinition = "text")
    private String rawProfile;

    public UserThirdPartyBinding(
        UserAccount user,
        String providerKey,
        AuthenticationProviderKind provider,
        String subject,
        String unionId,
        String displayName,
        String email,
        String mobile,
        String avatarUrl,
        String rawProfile
    ) {
        this.user = user;
        this.providerKey = providerKey;
        this.provider = provider;
        this.subject = subject;
        updateProfile(unionId, displayName, email, mobile, avatarUrl, rawProfile);
    }

    public void updateProfile(String unionId, String displayName, String email, String mobile, String avatarUrl, String rawProfile) {
        this.unionId = unionId;
        this.displayName = displayName;
        this.email = email;
        this.mobile = mobile;
        this.avatarUrl = avatarUrl;
        this.rawProfile = rawProfile;
    }
}
