package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.mapper.IdentitySourceMapper;
import com.antiam.repository.IdentitySourceConnectorRepository;
import com.antiam.repository.IdentitySourceRepository;
import com.antiam.repository.IdentitySyncJobRepository;
import com.antiam.repository.IdentitySyncRunRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import com.antiam.service.identitysource.DirectorySyncPayloadReader;
import com.antiam.service.identitysource.RealtimeSyncSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class IdentitySourceRealtimeSyncTest {

    private static final String SECRET = "realtime-secret";

    private final IdentitySourceRepository identitySources = mock(IdentitySourceRepository.class);
    private final IdentitySourceConnectorRepository connectors = mock(IdentitySourceConnectorRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final UserGroupRepository groups = mock(UserGroupRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final RealtimeSyncSignature signature = new RealtimeSyncSignature();
    private final IdentitySourceService service = new IdentitySourceService(
        identitySources,
        connectors,
        mock(IdentitySyncJobRepository.class),
        mock(IdentitySyncRunRepository.class),
        organizations,
        users,
        groups,
        mock(IdentitySourceMapper.class),
        mock(TenantService.class),
        auditService,
        new DirectorySyncPayloadReader(new ObjectMapper().findAndRegisterModules()),
        signature,
        List.of(),
        mock(PlatformTransactionManager.class));

    @Test
    void appliesSignedRealtimePayload() {
        UUID sourceId = UUID.randomUUID();
        IdentitySource source = source(sourceId);
        when(identitySources.findByCode("corp")).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.of(connector(source, SECRET)));
        Organization savedOrganization = new Organization("engineering", "Engineering", null);
        ReflectionTestUtils.setField(savedOrganization, "id", UUID.randomUUID());
        when(organizations.findByCode("engineering")).thenReturn(Optional.empty(), Optional.of(savedOrganization));
        when(organizations.save(any(Organization.class))).thenReturn(savedOrganization);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String body = """
            {
              "organizations": [{"code": "engineering", "name": "Engineering"}],
              "users": [{"username": "alice", "displayName": "Alice", "organizationCode": "engineering"}]
            }
            """;

        var result = service.receiveRealtimeEvent("corp", signature.sign(SECRET, body), body, "identity-source-realtime");

        assertThat(result.sourceCode()).isEqualTo("corp");
        assertThat(result.organizationsCreated()).isEqualTo(1);
        assertThat(result.usersCreated()).isEqualTo(1);
        verify(organizations).save(any(Organization.class));
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getIdentitySource()).isSameAs(source);
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPayloadWithWrongSignature() {
        UUID sourceId = UUID.randomUUID();
        IdentitySource source = source(sourceId);
        when(identitySources.findByCode("corp")).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.of(connector(source, SECRET)));
        String body = """
            {"organizations": [{"code": "engineering", "name": "Engineering"}]}
            """;

        assertThatThrownBy(() -> service.receiveRealtimeEvent("corp", "deadbeef", body, "identity-source-realtime"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signature is invalid");

        verify(organizations, never()).save(any(Organization.class));
    }

    @Test
    void rejectsWhenConnectorSecretIsMissing() {
        UUID sourceId = UUID.randomUUID();
        IdentitySource source = source(sourceId);
        when(identitySources.findByCode("corp")).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.of(connector(source, null)));

        assertThatThrownBy(() -> service.receiveRealtimeEvent("corp", "deadbeef", "{}", "identity-source-realtime"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Realtime sync secret is not configured");
    }

    @Test
    void doesNotOverwriteLocalAccountWithSameUsername() {
        UUID sourceId = UUID.randomUUID();
        IdentitySource source = source(sourceId);
        when(identitySources.findByCode("corp")).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.of(connector(source, SECRET)));
        UserAccount admin = new UserAccount("admin", "Administrator", "admin@example.com", null, null, null, null);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
        String body = """
            {"users": [{"username": "admin", "displayName": "Hijacked", "email": "evil@example.com"}]}
            """;

        var result = service.receiveRealtimeEvent("corp", signature.sign(SECRET, body), body, "identity-source-realtime");

        assertThat(result.usersUpdated()).isZero();
        assertThat(admin.getDisplayName()).isEqualTo("Administrator");
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getIdentitySource()).isNull();
    }

    @Test
    void rejectsCyclicOrganizationHierarchy() {
        UUID sourceId = UUID.randomUUID();
        IdentitySource source = source(sourceId);
        when(identitySources.findByCode("corp")).thenReturn(Optional.of(source));
        when(connectors.findByIdentitySourceId(sourceId)).thenReturn(Optional.of(connector(source, SECRET)));
        String body = """
            {"organizations": [
              {"code": "a", "name": "A", "parentCode": "b"},
              {"code": "b", "name": "B", "parentCode": "a"}
            ]}
            """;

        assertThatThrownBy(() -> service.receiveRealtimeEvent("corp", signature.sign(SECRET, body), body, "identity-source-realtime"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("循环");
        verify(organizations, never()).save(any(Organization.class));
    }

    private IdentitySource source(UUID id) {
        IdentitySource source = new IdentitySource("corp", "Corp Directory", null, IdentitySourceType.SCIM, null);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }

    private IdentitySourceConnector connector(IdentitySource source, String secretRef) {
        IdentitySourceConnector connector = new IdentitySourceConnector(source, "{}", secretRef);
        ReflectionTestUtils.setField(connector, "id", UUID.randomUUID());
        return connector;
    }
}
