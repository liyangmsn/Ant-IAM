package com.antiam.service;

import static com.antiam.dto.AccessDtos.ApplicationResponse;
import static com.antiam.dto.AccessDtos.ApplicationRoleResponse;
import static com.antiam.dto.AccessDtos.ApplicationAccessReviewEntryResponse;
import static com.antiam.dto.AccessDtos.ApplicationAccessRequestResponse;
import static com.antiam.dto.AccessDtos.ApplicationAccessDecisionResponse;
import static com.antiam.dto.AccessDtos.ApplicationAssignmentRequest;
import static com.antiam.dto.AccessDtos.ApplicationAssignmentResponse;
import static com.antiam.dto.AccessDtos.ApplicationGroupResponse;
import static com.antiam.dto.AccessDtos.ApplicationSsoConfigResponse;
import static com.antiam.dto.AccessDtos.ConfigureApplicationSsoRequest;
import static com.antiam.dto.AccessDtos.CreateApplicationGroupRequest;
import static com.antiam.dto.AccessDtos.CreateApplicationAccessRequest;
import static com.antiam.dto.AccessDtos.CreateApplicationRequest;
import static com.antiam.dto.AccessDtos.CreateGroupRequest;
import static com.antiam.dto.AccessDtos.CreatePermissionRequest;
import static com.antiam.dto.AccessDtos.CreateRoleRequest;
import static com.antiam.dto.AccessDtos.DecideApplicationAccessRequest;
import static com.antiam.dto.AccessDtos.GroupEffectiveAccessResponse;
import static com.antiam.dto.AccessDtos.GroupEffectivePermissionResponse;
import static com.antiam.dto.AccessDtos.GroupMemberResponse;
import static com.antiam.dto.AccessDtos.GroupResponse;
import static com.antiam.dto.AccessDtos.PermissionResponse;
import static com.antiam.dto.AccessDtos.PermissionImpactGroupResponse;
import static com.antiam.dto.AccessDtos.PermissionImpactResponse;
import static com.antiam.dto.AccessDtos.PermissionImpactRoleResponse;
import static com.antiam.dto.AccessDtos.PermissionImpactUserResponse;
import static com.antiam.dto.AccessDtos.RequestableApplicationResponse;
import static com.antiam.dto.AccessDtos.RoleResponse;
import static com.antiam.dto.AccessDtos.RoleImpactGroupResponse;
import static com.antiam.dto.AccessDtos.RoleImpactResponse;
import static com.antiam.dto.AccessDtos.RoleImpactUserResponse;
import static com.antiam.dto.AccessDtos.SelfServiceApplicationAccessRequest;
import static com.antiam.dto.AccessDtos.UpdateApplicationGroupRequest;
import static com.antiam.dto.AccessDtos.UpdateApplicationRequest;
import static com.antiam.dto.AccessDtos.UpdateGroupRequest;
import static com.antiam.dto.AccessDtos.UpdatePermissionRequest;
import static com.antiam.dto.AccessDtos.UpdateRoleRequest;
import static com.antiam.dto.AccessDtos.UserApplicationResponse;

import com.antiam.common.NotFoundException;
import com.antiam.domain.AccountStatus;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationAccessRequest;
import com.antiam.domain.ApplicationAccessRequestStatus;
import com.antiam.domain.ApplicationAssignment;
import com.antiam.domain.ApplicationGroup;
import com.antiam.domain.ApplicationSsoConfig;
import com.antiam.domain.Permission;
import com.antiam.domain.Role;
import com.antiam.domain.Tenant;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.repository.ApplicationAccessRequestRepository;
import com.antiam.repository.ApplicationAssignmentRepository;
import com.antiam.repository.ApplicationGroupRepository;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.ApplicationSsoConfigRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.CasServiceTicketRepository;
import com.antiam.repository.OAuthAccessTokenRepository;
import com.antiam.repository.OAuthAuthorizationCodeRepository;
import com.antiam.repository.OAuthConsentRepository;
import com.antiam.repository.OAuthRefreshTokenRepository;
import com.antiam.repository.PermissionRepository;
import com.antiam.repository.RoleRepository;
import com.antiam.repository.SamlAssertionRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final UserGroupRepository groups;
    private final ApplicationGroupRepository applicationGroups;
    private final ApplicationRepository applications;
    private final ApplicationSsoConfigRepository ssoConfigs;
    private final ApplicationAccessRequestRepository accessRequests;
    private final ApplicationAssignmentRepository applicationAssignments;
    private final OAuthAccessTokenRepository oauthAccessTokens;
    private final OAuthRefreshTokenRepository oauthRefreshTokens;
    private final OAuthAuthorizationCodeRepository oauthAuthorizationCodes;
    private final OAuthConsentRepository oauthConsents;
    private final SamlAssertionRepository samlAssertions;
    private final CasServiceTicketRepository casServiceTickets;
    private final AuthenticationSessionRepository authenticationSessions;
    private final AuthenticationEventRepository authenticationEvents;
    private final UserAccountRepository users;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建权限资源，权限后续通过角色授予用户或用户组。
     */
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request, String actor) {
        Permission saved = permissions.save(new Permission(request.code(), request.name(), request.description()));
        auditService.record(actor, "permission.create", "permission", saved.getId().toString(), saved.getCode());
        return toResponse(saved);
    }

    /**
     * 创建角色资源，角色用于聚合权限。
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request, String actor) {
        Role saved = roles.save(new Role(request.code(), request.name(), request.description()));
        auditService.record(actor, "role.create", "role", saved.getId().toString(), saved.getCode());
        return toResponse(saved);
    }

    /**
     * 创建用户组资源，用于聚合用户并继承角色。
     */
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String actor) {
        UserGroup saved = groups.save(new UserGroup(request.code(), request.name(), request.description()));
        auditService.record(actor, "group.create", "group", saved.getId().toString(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    // 创建接入应用，应用后续可配置 SSO、角色范围和访问授权。
    public ApplicationResponse createApplication(CreateApplicationRequest request, String actor) {
        Tenant tenant = request.tenantId() == null ? null : tenantService.getEntity(request.tenantId());
        ApplicationGroup group = request.groupId() == null ? null : getApplicationGroup(request.groupId());
        Application saved = applications.save(new Application(
            request.code(),
            request.name(),
            request.protocol(),
            request.loginUrl(),
            request.description(),
            tenant,
            group));
        auditService.record(actor, "application.create", "application", saved.getId().toString(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    // 更新应用基础资料，保留应用编码和既有授权关系。
    public ApplicationResponse updateApplication(UUID applicationId, UpdateApplicationRequest request, String actor) {
        Application application = getApplication(applicationId);
        ApplicationGroup group = request.groupId() == null ? null : getApplicationGroup(request.groupId());
        application.update(request.name(), request.protocol(), request.loginUrl(), request.description(), group);
        auditService.record(actor, "application.update", "application", applicationId.toString(), application.getCode());
        return toResponse(application);
    }

    @Transactional
    // 启用应用，使其重新参与访问决策和用户门户展示。
    public ApplicationResponse enableApplication(UUID applicationId, String actor) {
        Application application = getApplication(applicationId);
        application.enable();
        auditService.record(actor, "application.enable", "application", applicationId.toString(), application.getCode());
        return toResponse(application);
    }

    @Transactional
    // 停用应用，访问决策会拒绝该应用的新访问。
    public ApplicationResponse disableApplication(UUID applicationId, String actor) {
        Application application = getApplication(applicationId);
        application.disable();
        auditService.record(actor, "application.disable", "application", applicationId.toString(), application.getCode());
        return toResponse(application);
    }

    @Transactional
    // 删除应用及其协议配置、授权、访问申请和已签发凭据，供控制台应用管理执行硬删除。
    public void deleteApplication(UUID applicationId, String actor) {
        Application application = getApplication(applicationId);
        String code = application.getCode();
        cleanupApplicationReferences(applicationId);
        application.getRoles().clear();
        applications.delete(application);
        auditService.record(actor, "application.delete", "application", applicationId.toString(), code);
    }

    @Transactional(readOnly = true)
    // 查询应用分组列表，支持按分组编码、名称和备注关键字过滤。
    public List<ApplicationGroupResponse> listApplicationGroups(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return applicationGroups.findAll().stream()
            .filter(group -> matchesKeyword(group.getCode(), group.getName(), group.getDescription(), normalizedKeyword))
            .map(this::toApplicationGroupResponse)
            .toList();
    }

    @Transactional
    // 创建应用分组，用于控制台按业务场景组织应用。
    public ApplicationGroupResponse createApplicationGroup(CreateApplicationGroupRequest request, String actor) {
        ApplicationGroup saved = applicationGroups.save(new ApplicationGroup(request.code(), request.name(), request.description(), false));
        auditService.record(actor, "application_group.create", "application_group", saved.getId().toString(), saved.getCode());
        return toApplicationGroupResponse(saved);
    }

    @Transactional
    // 更新应用分组名称和备注，保留分组编码及已有应用归属。
    public ApplicationGroupResponse updateApplicationGroup(UUID groupId, UpdateApplicationGroupRequest request, String actor) {
        ApplicationGroup group = getApplicationGroup(groupId);
        group.update(request.name(), request.description());
        auditService.record(actor, "application_group.update", "application_group", groupId.toString(), group.getCode());
        return toApplicationGroupResponse(group);
    }

    @Transactional
    // 删除应用分组，分组下应用会自动回到未分组状态。
    public void deleteApplicationGroup(UUID groupId, String actor) {
        ApplicationGroup group = getApplicationGroup(groupId);
        String code = group.getCode();
        applications.findByGroupId(groupId).forEach(application -> application.update(
            application.getName(),
            application.getProtocol(),
            application.getLoginUrl(),
            application.getDescription(),
            null));
        applicationGroups.delete(group);
        auditService.record(actor, "application_group.delete", "application_group", groupId.toString(), code);
    }

    @Transactional(readOnly = true)
    // 查询应用可使用的角色集合，用于下发应用内角色映射。
    public List<ApplicationRoleResponse> listApplicationRoles(UUID applicationId) {
        return getApplication(applicationId).getRoles().stream()
            .map(this::toApplicationRoleResponse)
            .toList();
    }

    @Transactional
    // 将角色加入应用可用角色范围。
    public List<ApplicationRoleResponse> grantRoleToApplication(UUID applicationId, UUID roleId, String actor) {
        Application application = getApplication(applicationId);
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        application.grant(role);
        auditService.record(actor, "application.grant_role", "application", applicationId.toString(), role.getCode());
        return listApplicationRoles(applicationId);
    }

    @Transactional
    // 从应用可用角色范围中移除角色。
    public List<ApplicationRoleResponse> revokeRoleFromApplication(UUID applicationId, UUID roleId, String actor) {
        Application application = getApplication(applicationId);
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        application.revoke(role);
        auditService.record(actor, "application.revoke_role", "application", applicationId.toString(), role.getCode());
        return listApplicationRoles(applicationId);
    }

    @Transactional
    // 保存应用 SSO 配置，客户端密钥只保存加密后的哈希。
    public ApplicationSsoConfigResponse configureApplicationSso(UUID applicationId, ConfigureApplicationSsoRequest request, String actor) {
        Application application = getApplication(applicationId);
        String secretHash = request.clientSecret() == null || request.clientSecret().isBlank()
            ? null
            : passwordEncoder.encode(request.clientSecret());
        ApplicationSsoConfig replacement = new ApplicationSsoConfig(
            application,
            request.protocol(),
            request.clientId(),
            secretHash,
            joinValues(request.redirectUris()),
            joinValues(defaultSet(request.grantTypes(), Set.of("authorization_code", "refresh_token"))),
            request.pkceRequired(),
            joinValues(request.postLogoutRedirectUris()),
            request.loginInitiationUri(),
            positiveOrDefault(request.accessTokenTtlMinutes(), 20),
            positiveOrDefault(request.authorizationCodeTtlMinutes(), 5),
            positiveOrDefault(request.refreshTokenTtlMinutes(), 43_200),
            positiveOrDefault(request.idTokenTtlMinutes(), 30),
            request.reuseRefreshTokens() != null && request.reuseRefreshTokens(),
            defaultString(request.idTokenSignatureAlgorithm(), "RS256"),
            joinValues(request.scopes()),
            request.samlEntityId(),
            request.samlAcsUrl(),
            request.casServiceUrl(),
            request.jwtAudience(),
            request.formLoginTemplate(),
            joinValues(request.idTokenClaims()),
            joinEntries(request.customClaims()));
        ApplicationSsoConfig saved = ssoConfigs.findByApplicationId(applicationId)
            .map(existing -> {
                existing.replaceWith(replacement);
                return existing;
            })
            .orElseGet(() -> ssoConfigs.save(replacement));
        auditService.record(actor, "application.sso.configure", "application", applicationId.toString(), request.protocol().name());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 读取应用 SSO 配置，供登录编排和管理页展示使用。
    public ApplicationSsoConfigResponse getApplicationSso(UUID applicationId) {
        return ssoConfigs.findByApplicationId(applicationId)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("Application SSO config not found: " + applicationId));
    }

    @Transactional
    // 给用户或用户组分配应用访问权，同一主体重复授权时会重新启用并更新过期时间。
    public ApplicationAssignmentResponse assignApplication(UUID applicationId, ApplicationAssignmentRequest request, String actor) {
        if ((request.userId() == null && request.groupId() == null) || (request.userId() != null && request.groupId() != null)) {
            throw new IllegalArgumentException("Exactly one of userId or groupId is required");
        }
        Application application = getApplication(applicationId);
        UserAccount user = request.userId() == null ? null : users.findById(request.userId())
            .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        UserGroup group = request.groupId() == null ? null : groups.findById(request.groupId())
            .orElseThrow(() -> new NotFoundException("User group not found: " + request.groupId()));
        ApplicationAssignment saved = findExistingAssignment(applicationId, request)
            .map(existing -> {
                existing.enable();
                existing.updateExpiry(request.expiresAt());
                return existing;
            })
            .orElseGet(() -> applicationAssignments.save(new ApplicationAssignment(application, user, group, request.expiresAt())));
        auditService.record(actor, "application.assign", "application", applicationId.toString(), assignmentDetail(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询应用的授权记录列表。
    public List<ApplicationAssignmentResponse> listApplicationAssignments(UUID applicationId) {
        return applicationAssignments.findByApplicationId(applicationId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    // 汇总应用访问评审清单，覆盖直接授权、用户组授权和应用角色带来的访问。
    public List<ApplicationAccessReviewEntryResponse> reviewApplicationAccess(UUID applicationId, boolean includeDisabledAssignments) {
        Application application = getApplication(applicationId);
        List<ApplicationAccessReviewEntryResponse> assignmentEntries = applicationAssignments.findByApplicationId(applicationId).stream()
            .filter(assignment -> includeDisabledAssignments || assignment.isEnabled())
            .flatMap(assignment -> reviewAssignment(application, assignment).stream())
            .toList();
        List<ApplicationAccessReviewEntryResponse> roleEntries = reviewApplicationRoleAccess(application, assignmentEntries);
        return java.util.stream.Stream.concat(assignmentEntries.stream(), roleEntries.stream()).toList();
    }

    @Transactional
    // 启用应用授权记录，让授权主体重新获得访问能力。
    public ApplicationAssignmentResponse enableApplicationAssignment(UUID applicationId, UUID assignmentId, String actor) {
        ApplicationAssignment assignment = getApplicationAssignment(applicationId, assignmentId);
        assignment.enable();
        auditService.record(actor, "application_assignment.enable", "application", applicationId.toString(), assignmentDetail(assignment));
        return toResponse(assignment);
    }

    @Transactional
    // 停用应用授权记录，保留历史用于审计和复核。
    public ApplicationAssignmentResponse disableApplicationAssignment(UUID applicationId, UUID assignmentId, String actor) {
        ApplicationAssignment assignment = getApplicationAssignment(applicationId, assignmentId);
        assignment.disable();
        auditService.record(actor, "application_assignment.disable", "application", applicationId.toString(), assignmentDetail(assignment));
        return toResponse(assignment);
    }

    @Transactional(readOnly = true)
    // 判断用户是否可访问应用，按应用状态、用户状态、租户、直接授权、用户组授权和应用角色依次判定。
    public ApplicationAccessDecisionResponse decideApplicationAccess(UUID applicationId, UUID userId) {
        Application application = getApplication(applicationId);
        UserAccount user = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (!application.isEnabled()) {
            return new ApplicationAccessDecisionResponse(applicationId, userId, false, "application_disabled", null);
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            return new ApplicationAccessDecisionResponse(applicationId, userId, false, "user_not_active", null);
        }
        if (application.getTenant() != null && (user.getTenant() == null
            || !application.getTenant().getId().equals(user.getTenant().getId()))) {
            return new ApplicationAccessDecisionResponse(applicationId, userId, false, "tenant_mismatch", null);
        }
        java.util.Optional<ApplicationAssignment> direct = applicationAssignments.findByApplicationIdAndUserId(applicationId, userId)
            .filter(assignment -> assignment.isUsable(Instant.now()));
        if (direct.isPresent()) {
            return new ApplicationAccessDecisionResponse(applicationId, userId, true, "direct_assignment", direct.get().getId());
        }
        Set<UUID> groupIds = user.getGroups().stream().map(UserGroup::getId).collect(java.util.stream.Collectors.toSet());
        return applicationAssignments.findByApplicationId(applicationId).stream()
            .filter(assignment -> assignment.isUsable(Instant.now()))
            .filter(assignment -> assignment.getGroup() != null && groupIds.contains(assignment.getGroup().getId()))
            .findFirst()
            .map(assignment -> new ApplicationAccessDecisionResponse(
                applicationId,
                userId,
                true,
                "group_assignment",
                assignment.getId()))
            .orElseGet(() -> userHasApplicationRole(application, user)
                ? new ApplicationAccessDecisionResponse(applicationId, userId, true, "application_role", null)
                : new ApplicationAccessDecisionResponse(applicationId, userId, false, "no_assignment", null));
    }

    @Transactional(readOnly = true)
    // 查询指定用户当前可访问的应用门户列表。
    public List<UserApplicationResponse> listUserApplications(UUID userId) {
        UserAccount user = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        return listUserApplications(user);
    }

    @Transactional(readOnly = true)
    // 查询当前登录用户的应用门户列表。
    public List<UserApplicationResponse> listCurrentUserApplications(String username) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return listUserApplications(user);
    }

    @Transactional(readOnly = true)
    // 查询当前用户可自助申请的应用，排除已拥有访问权的应用并标记待审批申请。
    public List<RequestableApplicationResponse> listCurrentUserRequestableApplications(String username) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            return List.of();
        }
        Map<UUID, ApplicationAccessRequest> pendingRequests = accessRequests.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .filter(request -> request.getStatus() == ApplicationAccessRequestStatus.PENDING)
            .collect(java.util.stream.Collectors.toMap(
                request -> request.getApplication().getId(),
                request -> request,
                (first, second) -> first,
                java.util.LinkedHashMap::new));
        return applications.findAll().stream()
            .filter(Application::isEnabled)
            .filter(application -> tenantMatches(application, user))
            .filter(application -> !decideApplicationAccess(application.getId(), user.getId()).allowed())
            .map(application -> toRequestableApplication(application, pendingRequests.get(application.getId())))
            .toList();
    }

    @Transactional
    // 管理员为指定用户创建应用访问申请。
    public ApplicationAccessRequestResponse requestApplicationAccess(CreateApplicationAccessRequest request, String actor) {
        Application application = getApplication(request.applicationId());
        UserAccount user = users.findById(request.userId())
            .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        return requestApplicationAccess(application, user, request.reason(), actor);
    }

    @Transactional
    // 当前用户为自己提交应用访问申请。
    public ApplicationAccessRequestResponse requestCurrentUserApplicationAccess(
        SelfServiceApplicationAccessRequest request,
        String username
    ) {
        Application application = getApplication(request.applicationId());
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return requestApplicationAccess(application, user, request.reason(), username);
    }

    @Transactional(readOnly = true)
    // 查询应用访问申请列表，可按审批状态和用户过滤。
    public List<ApplicationAccessRequestResponse> listApplicationAccessRequests(ApplicationAccessRequestStatus status, UUID userId) {
        List<ApplicationAccessRequest> values;
        if (status != null) {
            values = accessRequests.findByStatusOrderByCreatedAtDesc(status);
            if (userId != null) {
                values = values.stream().filter(request -> request.getUser().getId().equals(userId)).toList();
            }
        } else if (userId != null) {
            values = accessRequests.findByUserIdOrderByCreatedAtDesc(userId);
        } else {
            values = accessRequests.findAll();
        }
        return values.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    // 查询当前用户提交的应用访问申请。
    public List<ApplicationAccessRequestResponse> listCurrentUserApplicationAccessRequests(
        ApplicationAccessRequestStatus status,
        String username
    ) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return listApplicationAccessRequests(status, user.getId());
    }

    @Transactional
    // 批准应用访问申请，并同步创建用户到应用的直接授权。
    public ApplicationAccessRequestResponse approveApplicationAccessRequest(
        UUID requestId,
        DecideApplicationAccessRequest request,
        String actor
    ) {
        ApplicationAccessRequest accessRequest = getAccessRequest(requestId);
        assignApplication(
            accessRequest.getApplication().getId(),
            new ApplicationAssignmentRequest(accessRequest.getUser().getId(), null, assignmentExpiresAt(request)),
            actor);
        accessRequest.approve(actor, decisionReason(request));
        auditService.record(actor, "application_access_request.approve", "application", accessRequest.getApplication().getId().toString(), accessRequest.getUser().getUsername());
        return toResponse(accessRequest);
    }

    @Transactional
    // 拒绝应用访问申请，并记录审批原因。
    public ApplicationAccessRequestResponse rejectApplicationAccessRequest(
        UUID requestId,
        DecideApplicationAccessRequest request,
        String actor
    ) {
        ApplicationAccessRequest accessRequest = getAccessRequest(requestId);
        accessRequest.reject(actor, decisionReason(request));
        auditService.record(actor, "application_access_request.reject", "application", accessRequest.getApplication().getId().toString(), accessRequest.getUser().getUsername());
        return toResponse(accessRequest);
    }

    @Transactional
    // 管理员取消待处理的应用访问申请。
    public ApplicationAccessRequestResponse cancelApplicationAccessRequest(
        UUID requestId,
        DecideApplicationAccessRequest request,
        String actor
    ) {
        ApplicationAccessRequest accessRequest = getAccessRequest(requestId);
        accessRequest.cancel(actor, decisionReason(request));
        auditService.record(actor, "application_access_request.cancel", "application", accessRequest.getApplication().getId().toString(), accessRequest.getUser().getUsername());
        return toResponse(accessRequest);
    }

    @Transactional
    // 当前用户取消自己的待处理应用访问申请。
    public ApplicationAccessRequestResponse cancelCurrentUserApplicationAccessRequest(
        UUID requestId,
        DecideApplicationAccessRequest request,
        String username
    ) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        ApplicationAccessRequest accessRequest = getAccessRequest(requestId);
        if (!accessRequest.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Application access request does not belong to current user");
        }
        accessRequest.cancel(username, decisionReason(request));
        auditService.record(username, "application_access_request.self_cancel", "application", accessRequest.getApplication().getId().toString(), user.getUsername());
        return toResponse(accessRequest);
    }

    private ApplicationAccessRequestResponse requestApplicationAccess(
        Application application,
        UserAccount user,
        String reason,
        String actor
    ) {
        if (!application.isEnabled()) {
            throw new IllegalArgumentException("Application is disabled");
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("User account is not active");
        }
        if (!tenantMatches(application, user)) {
            throw new IllegalArgumentException("Application and user tenant do not match");
        }
        ApplicationAccessDecisionResponse decision = decideApplicationAccess(application.getId(), user.getId());
        if (decision.allowed()) {
            throw new IllegalArgumentException("User already has access to application");
        }
        accessRequests.findByApplicationIdAndUserIdAndStatus(
                application.getId(),
                user.getId(),
                ApplicationAccessRequestStatus.PENDING)
            .ifPresent(existing -> {
                throw new IllegalArgumentException("A pending application access request already exists");
            });
        ApplicationAccessRequest saved = accessRequests.save(new ApplicationAccessRequest(application, user, reason, actor));
        auditService.record(actor, "application_access_request.create", "application", application.getId().toString(), user.getUsername());
        return toResponse(saved);
    }

    private List<UserApplicationResponse> listUserApplications(UserAccount user) {
        if (user.getStatus() != AccountStatus.ACTIVE) {
            return List.of();
        }
        Set<UUID> groupIds = user.getGroups().stream().map(UserGroup::getId).collect(java.util.stream.Collectors.toSet());
        return applications.findAll().stream()
            .filter(Application::isEnabled)
            .filter(application -> tenantMatches(application, user))
            .map(application -> toUserApplication(application, user, groupIds))
            .filter(Objects::nonNull)
            .toList();
    }

    @Transactional
    // 将权限授权给角色，角色下的用户或用户组会继承该权限。
    public RoleResponse grantPermissionToRole(UUID roleId, UUID permissionId, String actor) {
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        Permission permission = permissions.findById(permissionId)
            .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionId));
        role.grant(permission);
        auditService.record(actor, "role.grant_permission", "role", roleId.toString(), permission.getCode());
        return toResponse(role);
    }

    @Transactional
    // 从角色中移除权限，影响所有通过该角色获得权限的主体。
    public RoleResponse revokePermissionFromRole(UUID roleId, UUID permissionId, String actor) {
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        Permission permission = permissions.findById(permissionId)
            .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionId));
        role.revoke(permission);
        auditService.record(actor, "role.revoke_permission", "role", roleId.toString(), permission.getCode());
        return toResponse(role);
    }

    @Transactional
    // 将角色授予用户组，使组内成员继承角色权限。
    public GroupResponse grantRoleToGroup(UUID groupId, UUID roleId, String actor) {
        UserGroup group = groups.findById(groupId).orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        group.grant(role);
        auditService.record(actor, "group.grant_role", "group", groupId.toString(), role.getCode());
        return toResponse(group);
    }

    @Transactional
    // 从用户组中撤销角色，组内成员不再通过该组继承角色权限。
    public GroupResponse revokeRoleFromGroup(UUID groupId, UUID roleId, String actor) {
        UserGroup group = groups.findById(groupId).orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        group.revoke(role);
        auditService.record(actor, "group.revoke_role", "group", groupId.toString(), role.getCode());
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    // 查询全部权限，供权限管理页和授权选择器使用。
    public List<PermissionResponse> listPermissions() {
        return listPermissions(null);
    }

    @Transactional(readOnly = true)
    // 按关键字筛选权限，支持通过编码、名称和描述定位权限项。
    public List<PermissionResponse> listPermissions(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return permissions.findAll().stream()
            .filter(permission -> matchesKeyword(permission.getCode(), permission.getName(), permission.getDescription(), normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 读取单个权限详情，用于编辑和影响分析前的校验。
    public PermissionResponse getPermission(UUID permissionId) {
        Permission permission = permissions.findById(permissionId)
            .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionId));
        return toResponse(permission);
    }

    @Transactional
    // 更新权限名称和描述，权限编码保持稳定以保证策略引用不失效。
    public PermissionResponse updatePermission(UUID permissionId, UpdatePermissionRequest request, String actor) {
        Permission permission = permissions.findById(permissionId)
            .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionId));
        permission.update(request.name(), request.description());
        auditService.record(actor, "permission.update", "permission", permissionId.toString(), permission.getCode());
        return toResponse(permission);
    }

    @Transactional(readOnly = true)
    // 统计权限的角色、用户和用户组影响面，辅助管理员评估变更风险。
    public PermissionImpactResponse permissionImpact(UUID permissionId) {
        Permission permission = permissions.findById(permissionId)
            .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionId));
        List<Role> permissionRoles = roles.findByPermissionsId(permissionId);
        Map<UUID, PermissionUserImpactAccumulator> affectedUsers = new java.util.LinkedHashMap<>();
        Map<UUID, PermissionGroupImpactAccumulator> affectedGroups = new java.util.LinkedHashMap<>();
        permissionRoles.forEach(role -> {
            users.findByRolesId(role.getId()).forEach(user -> affectedUsers
                .computeIfAbsent(user.getId(), id -> new PermissionUserImpactAccumulator(user))
                .add("role:" + role.getCode()));
            groups.findByRolesId(role.getId()).forEach(group -> {
                affectedGroups
                    .computeIfAbsent(group.getId(), id -> new PermissionGroupImpactAccumulator(group))
                    .add(role.getCode());
                users.findByGroupsId(group.getId()).forEach(user -> affectedUsers
                    .computeIfAbsent(user.getId(), id -> new PermissionUserImpactAccumulator(user))
                    .add("group:" + group.getCode() + ":role:" + role.getCode()));
            });
        });
        return new PermissionImpactResponse(
            permission.getId(),
            permission.getCode(),
            permission.getName(),
            permission.getDescription(),
            permissionRoles.stream().map(role -> new PermissionImpactRoleResponse(
                role.getId(),
                role.getCode(),
                role.getName())).toList(),
            affectedUsers.values().stream().map(PermissionUserImpactAccumulator::toResponse).toList(),
            affectedGroups.values().stream().map(PermissionGroupImpactAccumulator::toResponse).toList());
    }

    @Transactional(readOnly = true)
    // 查询全部角色，返回角色包含的权限快照。
    public List<RoleResponse> listRoles() {
        return listRoles(null);
    }

    @Transactional(readOnly = true)
    // 按关键字筛选角色，便于在角色管理和授权界面快速定位。
    public List<RoleResponse> listRoles(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return roles.findAll().stream()
            .filter(role -> matchesKeyword(role.getCode(), role.getName(), role.getDescription(), normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 读取单个角色详情，包含角色直接持有的权限集合。
    public RoleResponse getRole(UUID roleId) {
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        return toResponse(role);
    }

    @Transactional
    // 更新角色展示信息，保持角色编码不变以保证外部引用稳定。
    public RoleResponse updateRole(UUID roleId, UpdateRoleRequest request, String actor) {
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        role.update(request.name(), request.description());
        auditService.record(actor, "role.update", "role", roleId.toString(), role.getCode());
        return toResponse(role);
    }

    @Transactional(readOnly = true)
    // 汇总角色影响面，展示直接授权用户、继承该角色的用户组和权限列表。
    public RoleImpactResponse roleImpact(UUID roleId) {
        Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        Set<String> permissionCodes = role.getPermissions().stream()
            .map(Permission::getCode)
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        List<RoleImpactUserResponse> directUsers = users.findByRolesId(roleId).stream()
            .map(user -> new RoleImpactUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus()))
            .toList();
        List<RoleImpactGroupResponse> inheritedByGroups = groups.findByRolesId(roleId).stream()
            .map(group -> new RoleImpactGroupResponse(
                group.getId(),
                group.getCode(),
                group.getName(),
                users.findByGroupsId(group.getId()).size()))
            .toList();
        return new RoleImpactResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            permissionCodes,
            directUsers,
            inheritedByGroups);
    }

    @Transactional(readOnly = true)
    // 查询全部用户组，返回用户组持有的角色概览。
    public List<GroupResponse> listGroups() {
        return listGroups(null);
    }

    @Transactional(readOnly = true)
    // 按关键字筛选用户组，支持通过组编码、名称和备注检索。
    public List<GroupResponse> listGroups(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return groups.findAll().stream()
            .filter(group -> matchesKeyword(group.getCode(), group.getName(), group.getDescription(), normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 读取用户组详情，包含用户组已经绑定的角色信息。
    public GroupResponse getGroup(UUID groupId) {
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        return toResponse(group);
    }

    @Transactional
    // 更新用户组名称和备注，组编码保持不变以便同步和审计追踪。
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, String actor) {
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        group.update(request.name(), request.description());
        auditService.record(actor, "group.update", "group", groupId.toString(), group.getCode());
        return toResponse(group);
    }

    @Transactional
    // 删除用户组，数据库外键会级联清理成员关系和角色关系。
    public void deleteGroup(UUID groupId, String actor) {
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        String code = group.getCode();
        groups.delete(group);
        auditService.record(actor, "group.delete", "group", groupId.toString(), code);
    }

    @Transactional(readOnly = true)
    // 查询用户组成员列表，用于成员管理和授权影响排查。
    public List<GroupMemberResponse> listGroupMembers(UUID groupId) {
        groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        return users.findByGroupsId(groupId).stream()
            .map(this::toGroupMemberResponse)
            .toList();
    }

    @Transactional
    // 向用户组添加成员，供用户组详情页直接维护成员关系。
    public GroupMemberResponse addGroupMember(UUID groupId, UUID userId, String actor) {
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        UserAccount user = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        user.join(group);
        auditService.record(actor, "group.member.add", "group", groupId.toString(), user.getUsername());
        return toGroupMemberResponse(user);
    }

    @Transactional
    // 从用户组移除成员。
    public void removeGroupMember(UUID groupId, UUID userId, String actor) {
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        UserAccount user = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        user.leave(group);
        auditService.record(actor, "group.member.remove", "group", groupId.toString(), user.getUsername());
    }

    @Transactional(readOnly = true)
    // 计算用户组有效访问权限，合并组内所有角色带来的权限。
    public GroupEffectiveAccessResponse groupEffectiveAccess(UUID groupId) {
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        Map<String, GroupPermissionAccumulator> permissions = new java.util.TreeMap<>();
        group.getRoles().forEach(role -> role.getPermissions().forEach(permission -> permissions
            .computeIfAbsent(permission.getCode(), code -> new GroupPermissionAccumulator(permission))
            .add(role.getCode())));
        Set<String> roleCodes = group.getRoles().stream()
            .map(Role::getCode)
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        return new GroupEffectiveAccessResponse(
            group.getId(),
            group.getCode(),
            group.getName(),
            roleCodes,
            permissions.values().stream().map(GroupPermissionAccumulator::toResponse).toList());
    }

    @Transactional(readOnly = true)
    // 按租户查询应用列表，兼容早期只按租户过滤的调用场景。
    public List<ApplicationResponse> listApplications(UUID tenantId) {
        return listApplications(tenantId, null, null);
    }

    @Transactional(readOnly = true)
    // 查询应用列表，支持租户、启用状态和关键字组合过滤。
    public List<ApplicationResponse> listApplications(UUID tenantId, Boolean enabled, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        List<Application> values = tenantId == null ? applications.findAll() : applications.findByTenantId(tenantId);
        return values.stream()
            .filter(application -> enabled == null || application.isEnabled() == enabled)
            .filter(application -> matchesKeyword(application.getCode(), application.getName(), application.getLoginUrl(), normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询应用详情响应，供控制器返回应用基础资料。
    public ApplicationResponse getApplicationResponse(UUID applicationId) {
        return toResponse(getApplication(applicationId));
    }

    private void cleanupApplicationReferences(UUID applicationId) {
        oauthAuthorizationCodes.deleteByApplicationId(applicationId);
        oauthAccessTokens.deleteByApplicationId(applicationId);
        oauthRefreshTokens.deleteByApplicationId(applicationId);
        oauthConsents.deleteByApplicationId(applicationId);
        samlAssertions.deleteByApplicationId(applicationId);
        casServiceTickets.deleteByApplicationId(applicationId);
        authenticationEvents.deleteByApplicationId(applicationId);
        authenticationSessions.deleteByApplicationId(applicationId);
        accessRequests.deleteByApplicationId(applicationId);
        applicationAssignments.deleteByApplicationId(applicationId);
        ssoConfigs.deleteByApplicationId(applicationId);
    }

    private Application getApplication(UUID applicationId) {
        return applications.findById(applicationId)
            .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));
    }

    private ApplicationGroup getApplicationGroup(UUID groupId) {
        return applicationGroups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("Application group not found: " + groupId));
    }

    private ApplicationAccessRequest getAccessRequest(UUID requestId) {
        return accessRequests.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Application access request not found: " + requestId));
    }

    private String decisionReason(DecideApplicationAccessRequest request) {
        return request == null ? null : request.reason();
    }

    private Instant assignmentExpiresAt(DecideApplicationAccessRequest request) {
        return request == null ? null : request.assignmentExpiresAt();
    }

    private boolean tenantMatches(Application application, UserAccount user) {
        return application.getTenant() == null || (user.getTenant() != null
            && application.getTenant().getId().equals(user.getTenant().getId()));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim().toLowerCase();
    }

    private boolean matchesKeyword(String code, String name, String description, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return contains(code, keyword) || contains(name, keyword) || contains(description, keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName(), permission.getDescription());
    }

    private RoleResponse toResponse(Role role) {
        Set<String> permissionCodes = role.getPermissions().stream().map(Permission::getCode).collect(java.util.stream.Collectors.toSet());
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), permissionCodes);
    }

    private GroupResponse toResponse(UserGroup group) {
        Set<String> roleCodes = group.getRoles().stream().map(Role::getCode).collect(java.util.stream.Collectors.toSet());
        return new GroupResponse(
            group.getId(),
            group.getCode(),
            group.getName(),
            group.getDescription(),
            roleCodes,
            users.findByGroupsId(group.getId()).size(),
            group.getCreatedAt(),
            group.getUpdatedAt());
    }

    private GroupMemberResponse toGroupMemberResponse(UserAccount user) {
        UUID organizationId = user.getOrganization() == null ? null : user.getOrganization().getId();
        UUID tenantId = user.getTenant() == null ? null : user.getTenant().getId();
        return new GroupMemberResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getStatus(),
            organizationId,
            tenantId);
    }

    private static final class GroupPermissionAccumulator {
        private final Permission permission;
        private final Set<String> roles = new java.util.TreeSet<>();

        private GroupPermissionAccumulator(Permission permission) {
            this.permission = permission;
        }

        private void add(String roleCode) {
            roles.add(roleCode);
        }

        private GroupEffectivePermissionResponse toResponse() {
            return new GroupEffectivePermissionResponse(
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                roles);
        }
    }

    private static final class PermissionUserImpactAccumulator {
        private final UserAccount user;
        private final Set<String> sources = new java.util.TreeSet<>();

        private PermissionUserImpactAccumulator(UserAccount user) {
            this.user = user;
        }

        private void add(String source) {
            sources.add(source);
        }

        private PermissionImpactUserResponse toResponse() {
            return new PermissionImpactUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                sources);
        }
    }

    private final class PermissionGroupImpactAccumulator {
        private final UserGroup group;
        private final Set<String> roles = new java.util.TreeSet<>();

        private PermissionGroupImpactAccumulator(UserGroup group) {
            this.group = group;
        }

        private void add(String roleCode) {
            roles.add(roleCode);
        }

        private PermissionImpactGroupResponse toResponse() {
            return new PermissionImpactGroupResponse(
                group.getId(),
                group.getCode(),
                group.getName(),
                users.findByGroupsId(group.getId()).size(),
                roles);
        }
    }

    private ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
            application.getId(),
            application.getCode(),
            application.getName(),
            application.getProtocol(),
            application.getLoginUrl(),
            application.getDescription(),
            application.getTenant() == null ? null : application.getTenant().getId(),
            application.getGroup() == null ? null : application.getGroup().getId(),
            application.isEnabled(),
            application.isEnabled());
    }

    private ApplicationGroupResponse toApplicationGroupResponse(ApplicationGroup group) {
        return new ApplicationGroupResponse(
            group.getId(),
            group.getCode(),
            group.getName(),
            group.getDescription(),
            group.isBuiltIn(),
            Math.toIntExact(applications.countByGroupId(group.getId())),
            group.getCreatedAt(),
            group.getUpdatedAt());
    }

    private ApplicationRoleResponse toApplicationRoleResponse(Role role) {
        return new ApplicationRoleResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription());
    }

    private ApplicationSsoConfigResponse toResponse(ApplicationSsoConfig config) {
        return new ApplicationSsoConfigResponse(
            config.getId(),
            config.getApplication().getId(),
            config.getProtocol(),
            config.getClientId(),
            splitValues(config.getRedirectUris()),
            splitValues(config.getGrantTypes()),
            config.isPkceRequired(),
            splitValues(config.getPostLogoutRedirectUris()),
            config.getLoginInitiationUri(),
            config.getAccessTokenTtlMinutes(),
            config.getAuthorizationCodeTtlMinutes(),
            config.getRefreshTokenTtlMinutes(),
            config.getIdTokenTtlMinutes(),
            config.isReuseRefreshTokens(),
            config.getIdTokenSignatureAlgorithm(),
            splitValues(config.getScopes()),
            config.getSamlEntityId(),
            config.getSamlAcsUrl(),
            config.getCasServiceUrl(),
            config.getJwtAudience(),
            splitValues(config.getIdTokenClaims()),
            splitEntries(config.getCustomClaims()),
            config.isEnabled());
    }

    private ApplicationAssignmentResponse toResponse(ApplicationAssignment assignment) {
        UUID userId = assignment.getUser() == null ? null : assignment.getUser().getId();
        UUID groupId = assignment.getGroup() == null ? null : assignment.getGroup().getId();
        return new ApplicationAssignmentResponse(
            assignment.getId(),
            assignment.getApplication().getId(),
            userId,
            groupId,
            assignment.getExpiresAt(),
            assignment.isExpired(Instant.now()),
            assignment.isEnabled());
    }

    private List<ApplicationAccessReviewEntryResponse> reviewAssignment(Application application, ApplicationAssignment assignment) {
        if (assignment.getUser() != null) {
            return List.of(toReviewEntry(application, assignment, assignment.getUser(), null));
        }
        if (assignment.getGroup() == null) {
            return List.of();
        }
        return users.findByGroupsId(assignment.getGroup().getId()).stream()
            .map(user -> toReviewEntry(application, assignment, user, assignment.getGroup()))
            .toList();
    }

    private ApplicationAccessReviewEntryResponse toReviewEntry(
        Application application,
        ApplicationAssignment assignment,
        UserAccount user,
        UserGroup group
    ) {
        return new ApplicationAccessReviewEntryResponse(
            application.getId(),
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getStatus(),
            group == null ? "direct" : "group",
            assignment.getId(),
            group == null ? null : group.getId(),
            group == null ? null : group.getCode(),
            assignment.getExpiresAt(),
            assignment.isExpired(Instant.now()),
            assignment.isEnabled());
    }

    private List<ApplicationAccessReviewEntryResponse> reviewApplicationRoleAccess(
        Application application,
        List<ApplicationAccessReviewEntryResponse> assignmentEntries
    ) {
        Set<UUID> assignmentUserIds = assignmentEntries.stream()
            .map(ApplicationAccessReviewEntryResponse::userId)
            .collect(java.util.stream.Collectors.toSet());
        Map<UUID, UserAccount> roleUsers = new java.util.LinkedHashMap<>();
        application.getRoles().forEach(role -> {
            users.findByRolesId(role.getId()).forEach(user -> roleUsers.putIfAbsent(user.getId(), user));
            groups.findByRolesId(role.getId()).forEach(group -> users.findByGroupsId(group.getId())
                .forEach(user -> roleUsers.putIfAbsent(user.getId(), user)));
        });
        return roleUsers.values().stream()
            .filter(user -> !assignmentUserIds.contains(user.getId()))
            .map(user -> new ApplicationAccessReviewEntryResponse(
                application.getId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                "application_role",
                null,
                null,
                null,
                null,
                false,
                true))
            .toList();
    }

    private ApplicationAccessRequestResponse toResponse(ApplicationAccessRequest request) {
        return new ApplicationAccessRequestResponse(
            request.getId(),
            request.getApplication().getId(),
            request.getUser().getId(),
            request.getStatus(),
            request.getReason(),
            request.getRequestedBy(),
            request.getDecisionReason(),
            request.getDecidedBy(),
            request.getDecidedAt());
    }

    private UserApplicationResponse toUserApplication(Application application, UserAccount user, Set<UUID> groupIds) {
        java.util.Optional<ApplicationAssignment> direct = applicationAssignments
            .findByApplicationIdAndUserId(application.getId(), user.getId())
            .filter(assignment -> assignment.isUsable(Instant.now()));
        if (direct.isPresent()) {
            return toUserApplication(application, "direct", direct.get());
        }
        return applicationAssignments.findByApplicationId(application.getId()).stream()
            .filter(assignment -> assignment.isUsable(Instant.now()))
            .filter(assignment -> assignment.getGroup() != null && groupIds.contains(assignment.getGroup().getId()))
            .findFirst()
            .map(assignment -> toUserApplication(application, "group", assignment))
            .orElseGet(() -> userHasApplicationRole(application, user)
                ? toUserApplication(application, "application_role", null)
                : null);
    }

    private UserApplicationResponse toUserApplication(Application application, String assignmentSource, ApplicationAssignment assignment) {
        return new UserApplicationResponse(
            application.getId(),
            application.getCode(),
            application.getName(),
            application.getProtocol(),
            application.getLoginUrl(),
            application.getTenant() == null ? null : application.getTenant().getId(),
            assignmentSource,
            assignment == null ? null : assignment.getId(),
            assignment == null ? null : assignment.getExpiresAt());
    }

    private boolean userHasApplicationRole(Application application, UserAccount user) {
        Set<UUID> applicationRoleIds = application.getRoles().stream()
            .map(Role::getId)
            .collect(java.util.stream.Collectors.toSet());
        if (applicationRoleIds.isEmpty()) {
            return false;
        }
        boolean directRole = user.getRoles().stream()
            .map(Role::getId)
            .anyMatch(applicationRoleIds::contains);
        if (directRole) {
            return true;
        }
        return user.getGroups().stream()
            .flatMap(group -> group.getRoles().stream())
            .map(Role::getId)
            .anyMatch(applicationRoleIds::contains);
    }

    private RequestableApplicationResponse toRequestableApplication(
        Application application,
        ApplicationAccessRequest pendingRequest
    ) {
        return new RequestableApplicationResponse(
            application.getId(),
            application.getCode(),
            application.getName(),
            application.getProtocol(),
            application.getLoginUrl(),
            application.getTenant() == null ? null : application.getTenant().getId(),
            pendingRequest != null,
            pendingRequest == null ? null : pendingRequest.getId());
    }

    private java.util.Optional<ApplicationAssignment> findExistingAssignment(UUID applicationId, ApplicationAssignmentRequest request) {
        if (request.userId() != null) {
            return applicationAssignments.findByApplicationIdAndUserId(applicationId, request.userId());
        }
        return applicationAssignments.findByApplicationIdAndGroupId(applicationId, request.groupId());
    }

    private ApplicationAssignment getApplicationAssignment(UUID applicationId, UUID assignmentId) {
        ApplicationAssignment assignment = applicationAssignments.findById(assignmentId)
            .orElseThrow(() -> new NotFoundException("Application assignment not found: " + assignmentId));
        if (!assignment.getApplication().getId().equals(applicationId)) {
            throw new IllegalArgumentException("Application assignment does not belong to application: " + applicationId);
        }
        return assignment;
    }

    private String assignmentDetail(ApplicationAssignment assignment) {
        if (assignment.getUser() != null) {
            return "user=" + assignment.getUser().getUsername();
        }
        return "group=" + assignment.getGroup().getCode();
    }

    private String joinValues(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join("\n", values);
    }

    private Set<String> defaultSet(Set<String> values, Set<String> defaults) {
        return values == null || values.isEmpty() ? defaults : values;
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Set<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split("\\R"))
            .filter(item -> !item.isBlank())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String joinEntries(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .map(entry -> entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue()))
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private Map<String, String> splitEntries(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(value.split("\\R"))
            .filter(item -> !item.isBlank())
            .map(item -> {
                int separator = item.indexOf('=');
                String key = separator < 0 ? item : item.substring(0, separator);
                String entryValue = separator < 0 ? "" : item.substring(separator + 1);
                return Map.entry(key, entryValue);
            })
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (first, second) -> second,
                java.util.LinkedHashMap::new));
    }
}
