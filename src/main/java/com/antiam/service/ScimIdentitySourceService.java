package com.antiam.service;

import static com.antiam.dto.ScimDtos.ScimEmail;
import static com.antiam.dto.ScimDtos.ScimMember;
import static com.antiam.dto.ScimDtos.ScimName;
import static com.antiam.dto.ScimDtos.ScimPhoneNumber;
import static com.antiam.dto.ScimSourceDtos.GROUP_SCHEMA;
import static com.antiam.dto.ScimSourceDtos.ORGANIZATION_SCHEMA;
import static com.antiam.dto.ScimSourceDtos.USER_EXTENSION_SCHEMA;
import static com.antiam.dto.ScimSourceDtos.USER_SCHEMA;

import com.antiam.common.ConflictException;
import com.antiam.common.NotFoundException;
import com.antiam.common.ScimException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.AccountStatus;
import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.dto.ScimSourceDtos.ScimMeta;
import com.antiam.dto.ScimSourceDtos.ScimReference;
import com.antiam.dto.ScimSourceDtos.ScimUserExtension;
import com.antiam.dto.ScimSourceDtos.SourceGroupRequest;
import com.antiam.dto.ScimSourceDtos.SourceGroupResponse;
import com.antiam.dto.ScimSourceDtos.SourceOrganizationRequest;
import com.antiam.dto.ScimSourceDtos.SourceOrganizationResponse;
import com.antiam.dto.ScimSourceDtos.SourceUserRequest;
import com.antiam.dto.ScimSourceDtos.SourceUserResponse;
import com.antiam.dto.ScimSourceDtos.SyncTokenResponse;
import com.antiam.dto.ScimSourceDtos.SyncTokenStatusResponse;
import com.antiam.repository.IdentitySourceConnectorRepository;
import com.antiam.repository.IdentitySourceRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通用 SCIM 身份源：第三方系统用同步令牌，经 /scim/v2/sources/{code}/** 推送组织、用户与用户组。
 *
 * <p>每个身份源只能读写自己推送的数据（identity_source_id = 本源），用自己的 externalId 引用组织与上级组织；
 * 删除用户为停用（可恢复），组织与用户组仅在为空时删除。
 */
@Service
@RequiredArgsConstructor
public class ScimIdentitySourceService {

    public static final String TOKEN_PREFIX = "isk_";
    private static final String REFERENCE_BY_ID = "id";

    private final IdentitySourceRepository identitySources;
    private final IdentitySourceConnectorRepository connectors;
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final UserGroupRepository groups;
    private final UserService userService;
    private final AuditService auditService;
    private final TokenSupport tokens;

    // ---------------------------------------------------------------- 同步令牌（控制台管理）

    /** 生成或重新生成同步令牌，旧令牌立即失效；明文只返回这一次。 */
    @Transactional
    public SyncTokenResponse issueToken(UUID identitySourceId, String baseUrl, String actor) {
        IdentitySource source = requireScimSource(identitySourceId);
        String token = TOKEN_PREFIX + tokens.generateToken(32);
        String hash = tokens.sha256(token);
        connectors.findByIdentitySourceId(source.getId())
            .ifPresentOrElse(
                connector -> connector.replace(connector.getConfiguration(), hash),
                () -> connectors.save(new IdentitySourceConnector(source, null, hash)));
        auditService.record(actor, "identity_source.scim_token.issue", "identity_source", source.getId().toString(), source.getCode());
        return new SyncTokenResponse(token, scimBaseUrl(baseUrl, source));
    }

    @Transactional
    public void revokeToken(UUID identitySourceId, String actor) {
        IdentitySource source = requireScimSource(identitySourceId);
        connectors.findByIdentitySourceId(source.getId()).ifPresent(connector -> connector.replace(connector.getConfiguration(), null));
        auditService.record(actor, "identity_source.scim_token.revoke", "identity_source", source.getId().toString(), source.getCode());
    }

    @Transactional(readOnly = true)
    public SyncTokenStatusResponse tokenStatus(UUID identitySourceId, String baseUrl) {
        IdentitySource source = requireScimSource(identitySourceId);
        boolean issued = connectors.findByIdentitySourceId(source.getId())
            .map(connector -> connector.getSecretRef() != null && !connector.getSecretRef().isBlank())
            .orElse(false);
        return new SyncTokenStatusResponse(
            issued,
            scimBaseUrl(baseUrl, source),
            source.getLastSyncedAt(),
            organizations.countByIdentitySourceId(source.getId()),
            users.countByIdentitySourceId(source.getId()),
            groups.countByIdentitySourceId(source.getId()));
    }

    /**
     * 按身份源编码与 Bearer 令牌确认调用方；失败一律返回 401，不区分"身份源不存在"和"令牌错误"。
     */
    @Transactional(readOnly = true)
    public UUID authenticate(String sourceCode, String authorization) {
        String presented = bearer(authorization);
        IdentitySource source = identitySources.findByCode(sourceCode).orElse(null);
        if (source == null || source.getType() != IdentitySourceType.SCIM || presented == null) {
            throw ScimException.unauthorized("Invalid identity source or sync token");
        }
        String expected = connectors.findByIdentitySourceId(source.getId())
            .filter(IdentitySourceConnector::isEnabled)
            .map(IdentitySourceConnector::getSecretRef)
            .orElse(null);
        String actual = tokens.sha256(presented);
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw ScimException.unauthorized("Invalid identity source or sync token");
        }
        if (!source.isEnabled()) {
            throw new ScimException(403, null, "Identity source is disabled: " + sourceCode);
        }
        return source.getId();
    }

    // ---------------------------------------------------------------- Users

    @Transactional(readOnly = true)
    public List<SourceUserResponse> listUsers(UUID sourceId, String location) {
        return users.findByIdentitySourceIdOrderByCreatedAtAsc(sourceId).stream().map(user -> toResponse(user, location)).toList();
    }

    @Transactional(readOnly = true)
    public SourceUserResponse getUser(UUID sourceId, UUID userId, String location) {
        return toResponse(sourceUser(sourceId, userId), location);
    }

    @Transactional
    public SourceUserResponse createUser(UUID sourceId, SourceUserRequest request, String location) {
        IdentitySource source = source(sourceId);
        String externalId = trimToNull(request.externalId());
        if (externalId != null && users.existsByIdentitySourceIdAndExternalId(sourceId, externalId)) {
            throw ScimException.uniqueness("User externalId already exists: " + externalId);
        }
        if (users.findByUsername(request.userName().trim()).isPresent()) {
            throw ScimException.uniqueness("userName already exists: " + request.userName());
        }
        UserAccount user = userService.createSourceUser(
            source,
            externalId,
            request.userName(),
            displayName(request),
            primaryEmail(request),
            primaryPhone(request),
            organizationOf(sourceId, request.enterprise()),
            actor(source));
        if (Boolean.FALSE.equals(request.active())) {
            userService.suspend(user.getId(), actor(source));
        }
        source.markSynced();
        return toResponse(user, location);
    }

    @Transactional
    public SourceUserResponse replaceUser(UUID sourceId, UUID userId, SourceUserRequest request, String location) {
        UserAccount user = sourceUser(sourceId, userId);
        if (!user.getUsername().equals(request.userName().trim())) {
            throw ScimException.mutability("userName cannot be changed");
        }
        String externalId = trimToNull(request.externalId());
        requireUniqueUserExternalId(sourceId, externalId, userId);
        IdentitySource source = source(sourceId);
        userService.updateSourceUser(
            user,
            externalId,
            displayName(request),
            primaryEmail(request),
            primaryPhone(request),
            organizationOf(sourceId, request.enterprise()),
            actor(source));
        applyActive(user, request.active() == null || request.active(), source);
        source.markSynced();
        return toResponse(user, location);
    }

    /**
     * PATCH 的结果以"当前值 + 变更"重新组装为完整资源后走 replace，保证校验规则一致。
     */
    @Transactional
    public SourceUserResponse patchUser(UUID sourceId, UUID userId, SourceUserPatch patch, String location) {
        UserAccount user = sourceUser(sourceId, userId);
        String organization = user.getOrganization() == null ? null : user.getOrganization().getId().toString();
        UserState state = new UserState(
            user.getExternalId(),
            user.getDisplayName(),
            user.getEmail(),
            user.getMobile(),
            user.getStatus() == AccountStatus.ACTIVE,
            organization == null ? null : new ScimReference(organization, REFERENCE_BY_ID));
        patch.apply(state);
        if (state.userName != null && !state.userName.equals(user.getUsername())) {
            throw ScimException.mutability("userName cannot be changed");
        }
        SourceUserRequest request = new SourceUserRequest(
            List.of(USER_SCHEMA),
            state.externalId,
            user.getUsername(),
            state.displayName,
            null,
            state.email == null ? List.of() : List.of(new ScimEmail(state.email, "work", true)),
            state.mobile == null ? List.of() : List.of(new ScimPhoneNumber(state.mobile, "work", true)),
            state.active,
            state.organization == null ? null : new ScimUserExtension(state.organization));
        return replaceUser(sourceId, userId, request, location);
    }

    /** SCIM DELETE 用户：停用账号并回收访问，保留账号以便恢复和审计。 */
    @Transactional
    public void deleteUser(UUID sourceId, UUID userId) {
        UserAccount user = sourceUser(sourceId, userId);
        IdentitySource source = source(sourceId);
        if (user.getStatus() == AccountStatus.ACTIVE) {
            userService.suspend(user.getId(), actor(source));
        }
        auditService.record(actor(source), "scim.user.deprovision", "identity_source", sourceId.toString(), user.getUsername());
        source.markSynced();
    }

    // ---------------------------------------------------------------- Organizations

    @Transactional(readOnly = true)
    public List<SourceOrganizationResponse> listOrganizations(UUID sourceId, String location) {
        return organizations.findByIdentitySourceIdOrderByCreatedAtAsc(sourceId).stream()
            .map(organization -> toResponse(sourceId, organization, location))
            .toList();
    }

    @Transactional(readOnly = true)
    public SourceOrganizationResponse getOrganization(UUID sourceId, UUID organizationId, String location) {
        return toResponse(sourceId, sourceOrganization(sourceId, organizationId), location);
    }

    @Transactional
    public SourceOrganizationResponse createOrganization(UUID sourceId, SourceOrganizationRequest request, String location) {
        IdentitySource source = source(sourceId);
        String externalId = request.externalId().trim();
        if (organizations.existsByIdentitySourceIdAndExternalId(sourceId, externalId)) {
            throw ScimException.uniqueness("Organization externalId already exists: " + externalId);
        }
        String code = sourceScopedCode(source, externalId);
        if (organizations.existsByCode(code)) {
            throw ScimException.uniqueness("Organization code already exists: " + code);
        }
        Organization parent = resolveOrganization(sourceId, request.parent(), "parent");
        Organization saved = organizations.save(new Organization(code, request.displayName().trim(), parent));
        saved.assignSource(source, externalId);
        auditService.record(actor(source), "scim.organization.create", "identity_source", sourceId.toString(), externalId);
        source.markSynced();
        return toResponse(sourceId, saved, location);
    }

    @Transactional
    public SourceOrganizationResponse replaceOrganization(UUID sourceId, UUID organizationId, SourceOrganizationRequest request, String location) {
        IdentitySource source = source(sourceId);
        Organization organization = sourceOrganization(sourceId, organizationId);
        if (!organization.getExternalId().equals(request.externalId().trim())) {
            throw ScimException.mutability("Organization externalId cannot be changed");
        }
        Organization parent = resolveOrganization(sourceId, request.parent(), "parent");
        for (Organization ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getId().equals(organization.getId())) {
                throw ScimException.invalidValue("Organization parent would create a cycle");
            }
        }
        organization.update(request.displayName().trim(), parent);
        auditService.record(actor(source), "scim.organization.update", "identity_source", sourceId.toString(), organization.getExternalId());
        source.markSynced();
        return toResponse(sourceId, organization, location);
    }

    @Transactional
    public void deleteOrganization(UUID sourceId, UUID organizationId) {
        IdentitySource source = source(sourceId);
        Organization organization = sourceOrganization(sourceId, organizationId);
        if (organizations.existsByParentId(organizationId)) {
            throw new ScimException(409, null, "Organization still has child organizations");
        }
        if (users.existsByOrganizationId(organizationId)) {
            throw new ScimException(409, null, "Organization still has users");
        }
        String externalId = organization.getExternalId();
        organizations.delete(organization);
        auditService.record(actor(source), "scim.organization.delete", "identity_source", sourceId.toString(), externalId);
        source.markSynced();
    }

    // ---------------------------------------------------------------- Groups

    @Transactional(readOnly = true)
    public List<SourceGroupResponse> listGroups(UUID sourceId, String location) {
        return groups.findByIdentitySourceIdOrderByCreatedAtAsc(sourceId).stream().map(group -> toResponse(group, location)).toList();
    }

    @Transactional(readOnly = true)
    public SourceGroupResponse getGroup(UUID sourceId, UUID groupId, String location) {
        return toResponse(sourceGroup(sourceId, groupId), location);
    }

    @Transactional(readOnly = true)
    public List<String> groupMemberIds(UUID sourceId, UUID groupId) {
        sourceGroup(sourceId, groupId);
        return users.findByGroupsId(groupId).stream().map(user -> user.getId().toString()).toList();
    }

    @Transactional
    public SourceGroupResponse createGroup(UUID sourceId, SourceGroupRequest request, String location) {
        IdentitySource source = source(sourceId);
        String externalId = request.externalId().trim();
        if (groups.existsByIdentitySourceIdAndExternalId(sourceId, externalId)) {
            throw ScimException.uniqueness("Group externalId already exists: " + externalId);
        }
        String code = sourceScopedCode(source, externalId);
        if (groups.existsByCode(code)) {
            throw ScimException.uniqueness("Group code already exists: " + code);
        }
        UserGroup saved = groups.save(new UserGroup(code, request.displayName().trim()));
        saved.assignSource(source, externalId);
        replaceMembers(sourceId, saved, memberIds(request.members()));
        auditService.record(actor(source), "scim.group.create", "identity_source", sourceId.toString(), externalId);
        source.markSynced();
        return toResponse(saved, location);
    }

    /**
     * 更新用户组名称与成员；memberIds 为 null 时成员保持不变。
     */
    @Transactional
    public SourceGroupResponse updateGroup(UUID sourceId, UUID groupId, String externalId, String displayName, List<String> memberIds, String location) {
        IdentitySource source = source(sourceId);
        UserGroup group = sourceGroup(sourceId, groupId);
        if (externalId != null && !externalId.trim().equals(group.getExternalId())) {
            throw ScimException.mutability("Group externalId cannot be changed");
        }
        if (displayName != null) {
            if (displayName.isBlank()) {
                throw ScimException.invalidValue("Group displayName is required");
            }
            group.rename(displayName.trim());
        }
        if (memberIds != null) {
            replaceMembers(sourceId, group, memberIds);
        }
        auditService.record(actor(source), "scim.group.update", "identity_source", sourceId.toString(), group.getExternalId());
        source.markSynced();
        return toResponse(group, location);
    }

    @Transactional
    public void deleteGroup(UUID sourceId, UUID groupId) {
        IdentitySource source = source(sourceId);
        UserGroup group = sourceGroup(sourceId, groupId);
        users.findByGroupsId(groupId).forEach(user -> user.leave(group));
        String externalId = group.getExternalId();
        groups.delete(group);
        auditService.record(actor(source), "scim.group.delete", "identity_source", sourceId.toString(), externalId);
        source.markSynced();
    }

    // ---------------------------------------------------------------- 内部辅助

    private void replaceMembers(UUID sourceId, UserGroup group, List<String> memberIds) {
        Set<UUID> target = new LinkedHashSet<>();
        for (String value : memberIds) {
            UUID id = parseId(value, "members.value");
            users.findByIdAndIdentitySourceId(id, sourceId)
                .orElseThrow(() -> ScimException.invalidValue("Group member is not a user of this identity source: " + value));
            target.add(id);
        }
        List<UserAccount> current = group.getId() == null ? List.of() : users.findByGroupsId(group.getId());
        current.stream().filter(user -> !target.contains(user.getId())).forEach(user -> user.leave(group));
        Set<UUID> existing = new LinkedHashSet<>();
        current.forEach(user -> existing.add(user.getId()));
        target.stream()
            .filter(id -> !existing.contains(id))
            .forEach(id -> users.findById(id).ifPresent(user -> user.join(group)));
    }

    private void applyActive(UserAccount user, boolean active, IdentitySource source) {
        if (active && user.getStatus() != AccountStatus.ACTIVE) {
            userService.activate(user.getId(), actor(source));
        } else if (!active && user.getStatus() == AccountStatus.ACTIVE) {
            userService.suspend(user.getId(), actor(source));
        }
    }

    private Organization organizationOf(UUID sourceId, ScimUserExtension extension) {
        return extension == null ? null : resolveOrganization(sourceId, extension.organization(), USER_EXTENSION_SCHEMA + ":organization");
    }

    /**
     * 解析组织引用：默认按本源 externalId 查找；type=id 时按 IAM UUID 查找，可以引用本地组织作为挂载点。
     */
    private Organization resolveOrganization(UUID sourceId, ScimReference reference, String field) {
        if (reference == null || reference.value() == null || reference.value().isBlank()) {
            return null;
        }
        String value = reference.value().trim();
        if (REFERENCE_BY_ID.equalsIgnoreCase(reference.type())) {
            Organization organization = organizations.findById(parseId(value, field))
                .orElseThrow(() -> ScimException.invalidValue(field + " not found: " + value));
            if (organization.getIdentitySource() != null && !organization.getIdentitySource().getId().equals(sourceId)) {
                throw ScimException.invalidValue(field + " belongs to another identity source: " + value);
            }
            return organization;
        }
        return organizations.findByIdentitySourceIdAndExternalId(sourceId, value)
            .orElseThrow(() -> ScimException.invalidValue(field + " not found by externalId: " + value));
    }

    private void requireUniqueUserExternalId(UUID sourceId, String externalId, UUID userId) {
        if (externalId != null && users.existsByIdentitySourceIdAndExternalIdAndIdNot(sourceId, externalId, userId)) {
            throw ScimException.uniqueness("User externalId already exists: " + externalId);
        }
    }

    private IdentitySource source(UUID sourceId) {
        return identitySources.findById(sourceId).orElseThrow(() -> ScimException.unauthorized("Identity source not found"));
    }

    private IdentitySource requireScimSource(UUID identitySourceId) {
        IdentitySource source = identitySources.findById(identitySourceId)
            .orElseThrow(() -> new NotFoundException("Identity source not found: " + identitySourceId));
        if (source.getType() != IdentitySourceType.SCIM) {
            throw new ConflictException("只有通用 SCIM 身份源可以生成同步令牌");
        }
        return source;
    }

    private UserAccount sourceUser(UUID sourceId, UUID userId) {
        return users.findByIdAndIdentitySourceId(userId, sourceId)
            .orElseThrow(() -> new ScimException(404, null, "User not found: " + userId));
    }

    private Organization sourceOrganization(UUID sourceId, UUID organizationId) {
        return organizations.findByIdAndIdentitySourceId(organizationId, sourceId)
            .orElseThrow(() -> new ScimException(404, null, "Organization not found: " + organizationId));
    }

    private UserGroup sourceGroup(UUID sourceId, UUID groupId) {
        return groups.findByIdAndIdentitySourceId(groupId, sourceId)
            .orElseThrow(() -> new ScimException(404, null, "Group not found: " + groupId));
    }

    private SourceUserResponse toResponse(UserAccount user, String location) {
        ScimEmail email = user.getEmail() == null ? null : new ScimEmail(user.getEmail(), "work", true);
        ScimPhoneNumber phone = user.getMobile() == null ? null : new ScimPhoneNumber(user.getMobile(), "work", true);
        Organization organization = user.getOrganization();
        ScimReference organizationRef = organization == null ? null : organizationReference(user.getIdentitySource(), organization);
        return new SourceUserResponse(
            organizationRef == null ? List.of(USER_SCHEMA) : List.of(USER_SCHEMA, USER_EXTENSION_SCHEMA),
            user.getId().toString(),
            user.getExternalId(),
            user.getUsername(),
            user.getDisplayName(),
            new ScimName(user.getDisplayName(), null, null),
            email == null ? List.of() : List.of(email),
            phone == null ? List.of() : List.of(phone),
            user.getStatus() == AccountStatus.ACTIVE,
            organizationRef == null ? null : new ScimUserExtension(organizationRef),
            meta("User", user.getCreatedAt(), user.getUpdatedAt(), location + "/Users/" + user.getId()));
    }

    private SourceOrganizationResponse toResponse(UUID sourceId, Organization organization, String location) {
        Organization parent = organization.getParent();
        return new SourceOrganizationResponse(
            List.of(ORGANIZATION_SCHEMA),
            organization.getId().toString(),
            organization.getExternalId(),
            organization.getName(),
            parent == null ? null : organizationReference(organization.getIdentitySource(), parent),
            meta("Organization", organization.getCreatedAt(), organization.getUpdatedAt(), location + "/Organizations/" + organization.getId()));
    }

    private SourceGroupResponse toResponse(UserGroup group, String location) {
        List<ScimMember> members = users.findByGroupsId(group.getId()).stream()
            .map(user -> new ScimMember(user.getId().toString(), user.getDisplayName(), location + "/Users/" + user.getId(), "User"))
            .toList();
        return new SourceGroupResponse(
            List.of(GROUP_SCHEMA),
            group.getId().toString(),
            group.getExternalId(),
            group.getName(),
            members,
            meta("Group", group.getCreatedAt(), group.getUpdatedAt(), location + "/Groups/" + group.getId()));
    }

    /** 本源组织用 externalId 回显；挂载点等非本源组织用 IAM id 回显。 */
    private static ScimReference organizationReference(IdentitySource source, Organization organization) {
        boolean sameSource = source != null && organization.getIdentitySource() != null
            && Objects.equals(organization.getIdentitySource().getId(), source.getId());
        return sameSource
            ? new ScimReference(organization.getExternalId(), "externalId", organization.getName())
            : new ScimReference(organization.getId().toString(), REFERENCE_BY_ID, organization.getName());
    }

    private static ScimMeta meta(String resourceType, Instant created, Instant lastModified, String location) {
        return new ScimMeta(
            resourceType,
            created == null ? null : created.toString(),
            lastModified == null ? null : lastModified.toString(),
            location);
    }

    private static List<String> memberIds(List<ScimMember> members) {
        if (members == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        members.forEach(member -> ids.add(member.value()));
        return ids;
    }

    private static String displayName(SourceUserRequest request) {
        if (request.displayName() != null && !request.displayName().isBlank()) {
            return request.displayName().trim();
        }
        if (request.name() != null && request.name().formatted() != null && !request.name().formatted().isBlank()) {
            return request.name().formatted().trim();
        }
        return request.userName().trim();
    }

    private static String primaryEmail(SourceUserRequest request) {
        if (request.emails() == null || request.emails().isEmpty()) {
            return null;
        }
        return request.emails().stream().filter(email -> Boolean.TRUE.equals(email.primary())).findFirst()
            .orElse(request.emails().getFirst()).value();
    }

    private static String primaryPhone(SourceUserRequest request) {
        if (request.phoneNumbers() == null || request.phoneNumbers().isEmpty()) {
            return null;
        }
        String value = request.phoneNumbers().stream().filter(phone -> Boolean.TRUE.equals(phone.primary())).findFirst()
            .orElse(request.phoneNumbers().getFirst()).value();
        return value == null || value.isBlank() ? null : value;
    }

    // 组织、用户组编码全局唯一，按"源编码:externalId"生成，避免与本地或其他身份源的数据冲突。
    private static String sourceScopedCode(IdentitySource source, String externalId) {
        return source.getCode() + ":" + externalId;
    }

    private static String actor(IdentitySource source) {
        return "scim:" + source.getCode();
    }

    private static String scimBaseUrl(String baseUrl, IdentitySource source) {
        return baseUrl + "/scim/v2/sources/" + source.getCode();
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ScimException.invalidValue(field + " must be an IAM id: " + value);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** PATCH 期间累积的用户属性。 */
    public static final class UserState {
        String userName;
        String externalId;
        String displayName;
        String email;
        String mobile;
        Boolean active;
        ScimReference organization;

        UserState(String externalId, String displayName, String email, String mobile, Boolean active, ScimReference organization) {
            this.externalId = externalId;
            this.displayName = displayName;
            this.email = email;
            this.mobile = mobile;
            this.active = active;
            this.organization = organization;
        }

        public void userName(String value) {
            this.userName = value;
        }

        public void externalId(String value) {
            this.externalId = value;
        }

        public void displayName(String value) {
            this.displayName = value;
        }

        public void email(String value) {
            this.email = value;
        }

        public void mobile(String value) {
            this.mobile = value;
        }

        public void active(Boolean value) {
            this.active = value;
        }

        public void organization(ScimReference value) {
            this.organization = value;
        }
    }

    /** 由控制器把 SCIM PatchOp 翻译成对 UserState 的修改。 */
    @FunctionalInterface
    public interface SourceUserPatch {
        void apply(UserState state);
    }
}
