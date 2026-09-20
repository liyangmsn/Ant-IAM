package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.common.TokenSupport;
import com.antiam.domain.UserAccount;
import com.antiam.dto.UserDtos.CreateUserRequest;
import com.antiam.repository.ApplicationAssignmentRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.MfaChallengeRepository;
import com.antiam.repository.MfaFactorRepository;
import com.antiam.repository.OAuthAccessTokenRepository;
import com.antiam.repository.OAuthRefreshTokenRepository;
import com.antiam.repository.PasswordResetTicketRepository;
import com.antiam.repository.RoleRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserCredentialHistoryRepository;
import com.antiam.repository.UserCredentialRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AuthenticationPolicyService authenticationPolicies = mock(AuthenticationPolicyService.class);
    private final UserService service = new UserService(
        users,
        mock(ApplicationAssignmentRepository.class),
        mock(UserGroupRepository.class),
        mock(RoleRepository.class),
        mock(UserCredentialRepository.class),
        mock(UserCredentialHistoryRepository.class),
        mock(PasswordResetTicketRepository.class),
        mock(MfaFactorRepository.class),
        mock(MfaChallengeRepository.class),
        mock(AuthenticationEventRepository.class),
        mock(AuthenticationSessionRepository.class),
        mock(OAuthAccessTokenRepository.class),
        mock(OAuthRefreshTokenRepository.class),
        mock(OrganizationService.class),
        mock(TenantService.class),
        authenticationPolicies,
        mock(AuditService.class),
        mock(PasswordEncoder.class),
        mock(TokenSupport.class),
        mock(SmsVerificationService.class),
        mock(MailDeliveryService.class));

    @Test
    void rejectsPasswordThatDoesNotMeetComplexityPolicy() {
        configurePolicy("number-letter", true, true, Set.of());

        assertThatThrownBy(() -> service.create(request("abcdefgh"), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("numbers and letters");
    }

    @Test
    void rejectsPasswordContainingUserProfileInformation() {
        configurePolicy("any", true, true, Set.of());

        assertThatThrownBy(() -> service.create(request("alice123X"), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("user profile");
    }

    @Test
    void rejectsConfiguredWeakPassword() {
        configurePolicy("any", false, true, Set.of());
        when(authenticationPolicies.currentAdditionalWeakPasswords()).thenReturn(Set.of("company2026"));

        assertThatThrownBy(() -> service.create(request("company2026"), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too weak");
    }

    @Test
    void rejectsSerialNumberExtensionRule() {
        configurePolicy("any", false, false, Set.of("serial-number"));

        assertThatThrownBy(() -> service.create(request("safe123X"), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serial numbers");
    }

    private void configurePolicy(String complexity, boolean checkUserInfo, boolean weakPasswordCheckEnabled, Set<String> extensionRules) {
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationPolicies.currentPasswordMinLength()).thenReturn(6);
        when(authenticationPolicies.currentPasswordMaxLength()).thenReturn(20);
        when(authenticationPolicies.currentPasswordComplexity()).thenReturn(complexity);
        when(authenticationPolicies.currentPasswordMaxRepeatedChars()).thenReturn(3);
        when(authenticationPolicies.currentPasswordCheckUserInfo()).thenReturn(checkUserInfo);
        when(authenticationPolicies.currentPasswordWeakPasswordCheckEnabled()).thenReturn(weakPasswordCheckEnabled);
        when(authenticationPolicies.currentAdditionalWeakPasswords()).thenReturn(Set.of());
        when(authenticationPolicies.currentPasswordExtensionRules()).thenReturn(extensionRules);
    }

    private CreateUserRequest request(String initialPassword) {
        return new CreateUserRequest(
            "alice",
            "Alice",
            "alice@example.com",
            "13800000000",
            null,
            null,
            initialPassword);
    }
}
