package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.common.ScimException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.AccountStatus;
import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.dto.ScimSourceDtos.ScimReference;
import com.antiam.dto.ScimSourceDtos.SourceOrganizationRequest;
import com.antiam.dto.ScimSourceDtos.SourceUserRequest;
import com.antiam.repository.IdentitySourceConnectorRepository;
import com.antiam.repository.IdentitySourceRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScimIdentitySourceServiceTest {

    private final IdentitySourceRepository identitySources = mock(IdentitySourceRepository.class);
    private final IdentitySourceConnectorRepository connectors = mock(IdentitySourceConnectorRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final UserService userService = mock(UserService.class);
    private final TokenSupport tokens = new TokenSupport();
    private final ScimIdentitySourceService service = new ScimIdentitySourceService(
        identitySources, connectors, organizations, users, mock(UserGroupRepository.class), userService, mock(AuditService.class), tokens);

    private final UUID sourceId = UUID.randomUUID();
    private final IdentitySource source = source("hr", IdentitySourceType.SCIM, sourceId);

    @Test
    void authenticatesWithTheSourceTokenOnly() {
        IdentitySourceConnector connector = new IdentitySourceConnector(source, null, tokens.sha256("isk_good"));
        when(identitySources.findByCode("hr")).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.of(connector));

        assertThat(service.authenticate("hr", "Bearer isk_good")).isEqualTo(sourceId);
        assertThatThrownBy(() -> service.authenticate("hr", "Bearer isk_bad")).isInstanceOf(ScimException.class)
            .extracting(ex -> ((ScimException) ex).status()).isEqualTo(401);
        assertThatThrownBy(() -> service.authenticate("hr", null)).isInstanceOf(ScimException.class);
        assertThatThrownBy(() -> service.authenticate("unknown", "Bearer isk_good")).isInstanceOf(ScimException.class)
            .extracting(ex -> ((ScimException) ex).status()).isEqualTo(401);
    }

    @Test
    void rejectsNonScimSourcesAndDisabledSources() {
        IdentitySource dingtalk = source("dt", IdentitySourceType.DINGTALK, UUID.randomUUID());
        when(identitySources.findByCode("dt")).thenReturn(Optional.of(dingtalk));
        assertThatThrownBy(() -> service.authenticate("dt", "Bearer isk_any")).isInstanceOf(ScimException.class)
            .extracting(ex -> ((ScimException) ex).status()).isEqualTo(401);

        source.disable();
        when(identitySources.findByCode("hr")).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.of(new IdentitySourceConnector(source, null, tokens.sha256("isk_good"))));
        assertThatThrownBy(() -> service.authenticate("hr", "Bearer isk_good")).isInstanceOf(ScimException.class)
            .extracting(ex -> ((ScimException) ex).status()).isEqualTo(403);
    }

    @Test
    void storesOnlyTheTokenHash() {
        when(identitySources.findById(sourceId)).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.empty());
        when(connectors.save(any(IdentitySourceConnector.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var issued = service.issueToken(sourceId, "https://iam.example.com", "admin");

        assertThat(issued.token()).startsWith("isk_");
        assertThat(issued.baseUrl()).isEqualTo("https://iam.example.com/scim/v2/sources/hr");
        verify(connectors).save(org.mockito.ArgumentMatchers.argThat(connector ->
            connector.getSecretRef().equals(tokens.sha256(issued.token())) && !connector.getSecretRef().contains(issued.token())));
    }

    @Test
    void cannotReachUsersOfAnotherSource() {
        UUID foreignUser = UUID.randomUUID();
        when(users.findByIdAndIdentitySourceId(foreignUser, sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(sourceId, foreignUser, "loc")).isInstanceOf(ScimException.class)
            .extracting(ex -> ((ScimException) ex).status()).isEqualTo(404);
        assertThatThrownBy(() -> service.deleteUser(sourceId, foreignUser)).isInstanceOf(ScimException.class);
        verify(userService, never()).suspend(any(), any());
    }

    @Test
    void creatingAUserWithATakenUserNameIsAUniquenessConflict() {
        when(identitySources.findById(sourceId)).thenReturn(Optional.of(source));
        when(users.findByUsername("admin")).thenReturn(Optional.of(mock(UserAccount.class)));

        assertThatThrownBy(() -> service.createUser(sourceId, userRequest("admin", null), "loc"))
            .isInstanceOf(ScimException.class)
            .satisfies(ex -> {
                assertThat(((ScimException) ex).status()).isEqualTo(409);
                assertThat(((ScimException) ex).scimType()).isEqualTo("uniqueness");
            });
    }

    @Test
    void organizationReferencesResolveByExternalIdWithinTheSourceOnly() {
        when(identitySources.findById(sourceId)).thenReturn(Optional.of(source));
        when(organizations.findByIdentitySourceIdAndExternalId(sourceId, "D-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrganization(sourceId,
            new SourceOrganizationRequest(null, "D2", "子部门", new ScimReference("D-MISSING", null)), "loc"))
            .isInstanceOf(ScimException.class)
            .extracting(ex -> ((ScimException) ex).scimType()).isEqualTo("invalidValue");
    }

    @Test
    void cannotMountUnderAnotherSourcesOrganization() {
        UUID foreignOrgId = UUID.randomUUID();
        Organization foreign = new Organization("other:D1", "其他源部门", null);
        foreign.assignSource(source("other", IdentitySourceType.SCIM, UUID.randomUUID()), "D1");
        when(identitySources.findById(sourceId)).thenReturn(Optional.of(source));
        when(organizations.findById(foreignOrgId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.createOrganization(sourceId,
            new SourceOrganizationRequest(null, "D9", "挂载", new ScimReference(foreignOrgId.toString(), "id")), "loc"))
            .isInstanceOf(ScimException.class)
            .hasMessageContaining("another identity source");
    }

    @Test
    void deletingAUserSuspendsInsteadOfRemoving() {
        UUID userId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(identitySources.findById(sourceId)).thenReturn(Optional.of(source));
        when(users.findByIdAndIdentitySourceId(userId, sourceId)).thenReturn(Optional.of(user));

        service.deleteUser(sourceId, userId);

        verify(userService).suspend(userId, "scim:hr");
        verify(users, never()).delete(any());
    }

    @Test
    void organizationWithChildrenOrUsersIsNotDeleted() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("hr:D1", "部门", null);
        organization.assignSource(source, "D1");
        when(identitySources.findById(sourceId)).thenReturn(Optional.of(source));
        when(organizations.findByIdAndIdentitySourceId(orgId, sourceId)).thenReturn(Optional.of(organization));
        when(organizations.existsByParentId(orgId)).thenReturn(false);
        UserAccount active = new UserAccount("on.duty", "在职", null, null, null, organization);
        when(users.findByOrganizationId(orgId)).thenReturn(List.of(active));

        assertThatThrownBy(() -> service.deleteOrganization(sourceId, orgId)).isInstanceOf(ScimException.class)
            .extracting(ex -> ((ScimException) ex).status()).isEqualTo(409);
        verify(organizations, never()).delete(any());
    }

    @Test
    void organizationWithOnlyDepartedUsersIsDeletedAndUsersDetached() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("hr:D1", "部门", null);
        organization.assignSource(source, "D1");
        UserAccount departed = new UserAccount("left", "已离职", null, null, null, organization);
        departed.suspend();
        when(identitySources.findById(sourceId)).thenReturn(Optional.of(source));
        when(organizations.findByIdAndIdentitySourceId(orgId, sourceId)).thenReturn(Optional.of(organization));
        when(users.findByOrganizationId(orgId)).thenReturn(List.of(departed));

        service.deleteOrganization(sourceId, orgId);

        assertThat(departed.getOrganization()).isNull();
        verify(organizations).delete(organization);
    }

    private static SourceUserRequest userRequest(String userName, String externalId) {
        return new SourceUserRequest(null, externalId, userName, null, null, List.of(), List.of(), true, null);
    }

    private static IdentitySource source(String code, IdentitySourceType type, UUID id) {
        IdentitySource source = new IdentitySource(code, code, null, type, null);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }
}
