package com.antiam.web;

import static com.antiam.dto.AccessDtos.GrantRequest;
import static com.antiam.dto.UserDtos.ChangeOwnPasswordRequest;
import static com.antiam.dto.UserDtos.CreateUserRequest;
import static com.antiam.dto.UserDtos.ConsumePasswordResetTicketRequest;
import static com.antiam.dto.UserDtos.CreatePasswordResetTicketRequest;
import static com.antiam.dto.UserDtos.MfaChallengeDetailResponse;
import static com.antiam.dto.UserDtos.MfaChallengeResponse;
import static com.antiam.dto.UserDtos.MfaFactorResponse;
import static com.antiam.dto.UserDtos.PasswordResetTicketResponse;
import static com.antiam.dto.UserDtos.PasswordResetTicketDetailResponse;
import static com.antiam.dto.UserDtos.RegisterMfaFactorRequest;
import static com.antiam.dto.UserDtos.RecoveryCodesResponse;
import static com.antiam.dto.UserDtos.SetPasswordRequest;
import static com.antiam.dto.UserDtos.StartMfaChallengeRequest;
import static com.antiam.dto.UserDtos.UpdateMfaFactorRequest;
import static com.antiam.dto.UserDtos.UpdateUserRequest;
import static com.antiam.dto.UserDtos.UserEffectiveAccessResponse;
import static com.antiam.dto.UserDtos.UserResponse;
import static com.antiam.dto.UserDtos.VerifyMfaChallengeRequest;
import static com.antiam.dto.UserDtos.VerifyMfaChallengeResponse;
import static com.antiam.dto.UserDtos.VerifyPasswordRequest;
import static com.antiam.dto.UserDtos.VerifyPasswordResponse;

import com.antiam.domain.AccountStatus;
import com.antiam.domain.MfaChallengeStatus;
import com.antiam.domain.MfaFactorType;
import com.antiam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户账号", description = "用户目录、生命周期、密码、MFA 和授权关系管理接口")
public class UserController {

    private final UserService users;

    /**
     * 查询用户目录，支持按租户、组织、状态和关键字过滤。
     */
    @Operation(summary = "查询用户列表", description = "按租户、组织、账号状态和关键字检索用户目录。")
    @GetMapping
    List<UserResponse> list(
        @Parameter(description = "租户 UUID") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "组织 UUID") @RequestParam(required = false) UUID organizationId,
        @Parameter(description = "账号状态") @RequestParam(required = false) AccountStatus status,
        @Parameter(description = "关键字，匹配用户名、显示名、邮箱或手机号") @RequestParam(required = false) String keyword
    ) {
        return users.list(tenantId, organizationId, status, keyword);
    }

    /**
     * 创建用户账号，可选初始化临时密码。
     */
    @Operation(summary = "创建用户", description = "创建用户账号，可绑定租户和组织，并可设置初始临时密码。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Parameter(description = "用户创建请求") @Valid @RequestBody CreateUserRequest request, Principal principal) {
        return users.create(request, principal.getName());
    }

    /**
     * 查询当前登录用户资料。
     */
    @Operation(summary = "获取当前用户", description = "根据当前认证主体返回用户资料。")
    @GetMapping("/me")
    UserResponse currentUser(Principal principal) {
        return users.currentUser(principal.getName());
    }

    /**
     * 按用户 ID 查询用户资料。
     */
    @Operation(summary = "获取用户详情", description = "根据用户 UUID 返回用户资料、用户组和直接角色。")
    @GetMapping("/{userId}")
    UserResponse get(@Parameter(description = "用户 UUID") @PathVariable UUID userId) {
        return users.get(userId);
    }

    /**
     * 更新用户基础资料和所属组织。
     */
    @Operation(summary = "更新用户", description = "更新显示名、邮箱、手机号和所属组织。")
    @PutMapping("/{userId}")
    UserResponse update(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "用户更新请求") @Valid @RequestBody UpdateUserRequest request,
        Principal principal
    ) {
        return users.update(userId, request, principal.getName());
    }

    /**
     * 激活用户账号，并清理密码失败计数。
     */
    @Operation(summary = "激活用户", description = "将用户账号恢复为 ACTIVE 状态，并重置密码失败计数。")
    @PostMapping("/{userId}/activate")
    UserResponse activate(@Parameter(description = "用户 UUID") @PathVariable UUID userId, Principal principal) {
        return users.activate(userId, principal.getName());
    }

    /**
     * 暂停用户账号，并回收活跃访问能力。
     */
    @Operation(summary = "暂停用户", description = "暂停账号并禁用直接应用授权、结束活跃会话、撤销 OAuth token。")
    @PostMapping("/{userId}/suspend")
    UserResponse suspend(@Parameter(description = "用户 UUID") @PathVariable UUID userId, Principal principal) {
        return users.suspend(userId, principal.getName());
    }

    /**
     * 锁定用户账号，并回收活跃访问能力。
     */
    @Operation(summary = "锁定用户", description = "锁定账号并禁用直接应用授权、结束活跃会话、撤销 OAuth token。")
    @PostMapping("/{userId}/lock")
    UserResponse lock(@Parameter(description = "用户 UUID") @PathVariable UUID userId, Principal principal) {
        return users.lock(userId, principal.getName());
    }

    /**
     * 将用户标记为离职，并执行访问回收。
     */
    @Operation(summary = "用户离职", description = "将账号标记为 DEPARTED，并禁用直接应用授权、结束活跃会话、撤销 OAuth token。")
    @PostMapping("/{userId}/depart")
    UserResponse depart(@Parameter(description = "用户 UUID") @PathVariable UUID userId, Principal principal) {
        return users.depart(userId, principal.getName());
    }

    /**
     * 将用户加入用户组。
     */
    @Operation(summary = "加入用户组", description = "通过 GrantRequest.subjectId 指定用户，targetId 指定用户组。")
    @PostMapping("/group-memberships")
    UserResponse joinGroup(@Parameter(description = "用户和用户组绑定请求") @Valid @RequestBody GrantRequest request, Principal principal) {
        return users.joinGroup(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 将用户移出用户组。
     */
    @Operation(summary = "移出用户组", description = "通过 GrantRequest.subjectId 指定用户，targetId 指定用户组。")
    @DeleteMapping("/group-memberships")
    UserResponse leaveGroup(@Parameter(description = "用户和用户组解绑请求") @Valid @RequestBody GrantRequest request, Principal principal) {
        return users.leaveGroup(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 给用户授予直接角色。
     */
    @Operation(summary = "授予用户角色", description = "通过 GrantRequest.subjectId 指定用户，targetId 指定角色。")
    @PostMapping("/role-assignments")
    UserResponse grantRole(@Parameter(description = "用户和角色绑定请求") @Valid @RequestBody GrantRequest request, Principal principal) {
        return users.grantRole(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 撤销用户直接角色。
     */
    @Operation(summary = "撤销用户角色", description = "通过 GrantRequest.subjectId 指定用户，targetId 指定角色。")
    @DeleteMapping("/role-assignments")
    UserResponse revokeRole(@Parameter(description = "用户和角色解绑请求") @Valid @RequestBody GrantRequest request, Principal principal) {
        return users.revokeRole(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 查询用户最终生效的角色和权限。
     */
    @Operation(summary = "查询用户有效权限", description = "聚合用户直接角色和用户组角色，返回最终生效权限。")
    @GetMapping("/{userId}/effective-access")
    UserEffectiveAccessResponse effectiveAccess(@Parameter(description = "用户 UUID") @PathVariable UUID userId) {
        return users.effectiveAccess(userId);
    }

    /**
     * 管理员为用户设置密码。
     */
    @Operation(summary = "设置用户密码", description = "管理员为指定用户设置密码，可标记为临时密码。")
    @PostMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setPassword(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "密码设置请求") @Valid @RequestBody SetPasswordRequest request,
        Principal principal
    ) {
        users.setPassword(userId, request, principal.getName());
    }

    /**
     * 当前用户自助修改密码。
     */
    @Operation(summary = "当前用户修改密码", description = "校验当前密码后修改为新密码。")
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeOwnPassword(@Parameter(description = "自助改密请求") @Valid @RequestBody ChangeOwnPasswordRequest request, Principal principal) {
        users.changeOwnPassword(principal.getName(), request);
    }

    /**
     * 创建密码重置票据，返回一次性明文 resetToken。
     */
    @Operation(summary = "创建密码重置票据", description = "为指定用户创建密码重置票据；响应中的 resetToken 只在创建时返回一次。")
    @PostMapping("/password-reset-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    PasswordResetTicketResponse createPasswordResetTicket(
        @Parameter(description = "密码重置票据创建请求") @Valid @RequestBody CreatePasswordResetTicketRequest request,
        Principal principal
    ) {
        return users.createPasswordResetTicket(request, principal.getName());
    }

    /**
     * 查询密码重置票据列表，响应不包含明文 token 或 hash。
     */
    @Operation(summary = "查询密码重置票据", description = "按用户、是否已使用、是否可用和关键字过滤密码重置票据。")
    @GetMapping("/password-reset-tickets")
    List<PasswordResetTicketDetailResponse> passwordResetTickets(
        @Parameter(description = "用户 UUID") @RequestParam(required = false) UUID userId,
        @Parameter(description = "是否已使用或作废") @RequestParam(required = false) Boolean consumed,
        @Parameter(description = "是否仍可使用") @RequestParam(required = false) Boolean usable,
        @Parameter(description = "关键字，匹配用户名、显示名或请求人") @RequestParam(required = false) String keyword,
        @Parameter(description = "最大返回数量，默认 100，最大 500") @RequestParam(required = false) Integer limit
    ) {
        return users.listPasswordResetTickets(userId, consumed, usable, keyword, limit);
    }

    /**
     * 查询单个密码重置票据详情。
     */
    @Operation(summary = "获取密码重置票据详情", description = "根据票据 UUID 返回票据元数据，不返回 resetToken 或 tokenHash。")
    @GetMapping("/password-reset-tickets/{ticketId}")
    PasswordResetTicketDetailResponse passwordResetTicket(@Parameter(description = "密码重置票据 UUID") @PathVariable UUID ticketId) {
        return users.getPasswordResetTicket(ticketId);
    }

    /**
     * 作废密码重置票据。
     */
    @Operation(summary = "作废密码重置票据", description = "将密码重置票据标记为不可用。")
    @PostMapping("/password-reset-tickets/{ticketId}/revoke")
    PasswordResetTicketDetailResponse revokePasswordResetTicket(
        @Parameter(description = "密码重置票据 UUID") @PathVariable UUID ticketId,
        Principal principal
    ) {
        return users.revokePasswordResetTicket(ticketId, principal.getName());
    }

    /**
     * 使用公开 resetToken 消费密码重置票据。
     */
    @Operation(summary = "消费密码重置票据", description = "使用 resetToken 设置新密码；该接口允许未登录用户访问。")
    @PostMapping("/password-reset-tickets/consumptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void consumePasswordResetTicket(@Parameter(description = "密码重置票据消费请求") @Valid @RequestBody ConsumePasswordResetTicketRequest request) {
        users.consumePasswordResetTicket(request);
    }

    /**
     * 校验用户名密码，并返回密码状态信息。
     */
    @Operation(summary = "校验密码", description = "校验用户名和密码，返回失败次数、锁定状态、过期状态等信息。")
    @PostMapping("/password-verifications")
    VerifyPasswordResponse verifyPassword(@Parameter(description = "密码校验请求") @Valid @RequestBody VerifyPasswordRequest request, Principal principal) {
        return users.verifyPassword(request.username(), request.password(), principal.getName());
    }

    /**
     * 查询用户已注册的 MFA 因子。
     */
    @Operation(summary = "查询用户 MFA 因子", description = "返回指定用户已注册的 MFA 因子列表。")
    @GetMapping("/{userId}/mfa-factors")
    List<MfaFactorResponse> mfaFactors(@Parameter(description = "用户 UUID") @PathVariable UUID userId) {
        return users.listMfaFactors(userId);
    }

    /**
     * 注册用户 MFA 因子。
     */
    @Operation(summary = "注册 MFA 因子", description = "为指定用户注册 MFA 因子，TOTP 可自动生成密钥。")
    @PostMapping("/{userId}/mfa-factors")
    @ResponseStatus(HttpStatus.CREATED)
    MfaFactorResponse registerMfaFactor(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "MFA 因子注册请求") @Valid @RequestBody RegisterMfaFactorRequest request,
        Principal principal
    ) {
        return users.registerMfaFactor(userId, request, principal.getName());
    }

    /**
     * 查询单个 MFA 因子详情。
     */
    @Operation(summary = "获取 MFA 因子详情", description = "根据用户 UUID 和因子 UUID 返回 MFA 因子详情。")
    @GetMapping("/{userId}/mfa-factors/{factorId}")
    MfaFactorResponse mfaFactor(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "MFA 因子 UUID") @PathVariable UUID factorId
    ) {
        return users.getMfaFactor(userId, factorId);
    }

    /**
     * 更新 MFA 因子名称。
     */
    @Operation(summary = "更新 MFA 因子", description = "更新指定 MFA 因子的展示名称。")
    @PutMapping("/{userId}/mfa-factors/{factorId}")
    MfaFactorResponse updateMfaFactor(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "MFA 因子 UUID") @PathVariable UUID factorId,
        @Parameter(description = "MFA 因子更新请求") @Valid @RequestBody UpdateMfaFactorRequest request,
        Principal principal
    ) {
        return users.updateMfaFactor(userId, factorId, request, principal.getName());
    }

    /**
     * 启用 MFA 因子。
     */
    @Operation(summary = "启用 MFA 因子", description = "将指定 MFA 因子设置为可用于挑战验证。")
    @PostMapping("/{userId}/mfa-factors/{factorId}/enable")
    MfaFactorResponse enableMfaFactor(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "MFA 因子 UUID") @PathVariable UUID factorId,
        Principal principal
    ) {
        return users.enableMfaFactor(userId, factorId, principal.getName());
    }

    /**
     * 停用 MFA 因子。
     */
    @Operation(summary = "停用 MFA 因子", description = "停用指定 MFA 因子，保留历史挑战记录。")
    @PostMapping("/{userId}/mfa-factors/{factorId}/disable")
    MfaFactorResponse disableMfaFactor(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "MFA 因子 UUID") @PathVariable UUID factorId,
        Principal principal
    ) {
        return users.disableMfaFactor(userId, factorId, principal.getName());
    }

    /**
     * 删除没有挑战历史的 MFA 因子。
     */
    @Operation(summary = "删除 MFA 因子", description = "删除未产生挑战历史的 MFA 因子；已有挑战历史的因子只能停用。")
    @DeleteMapping("/{userId}/mfa-factors/{factorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMfaFactor(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "MFA 因子 UUID") @PathVariable UUID factorId,
        Principal principal
    ) {
        users.deleteMfaFactor(userId, factorId, principal.getName());
    }

    /**
     * 生成或重置用户恢复码。
     */
    @Operation(summary = "生成 MFA 恢复码", description = "生成一组新的恢复码，旧恢复码会被替换。")
    @PostMapping("/{userId}/mfa-recovery-codes")
    @ResponseStatus(HttpStatus.CREATED)
    RecoveryCodesResponse generateRecoveryCodes(@Parameter(description = "用户 UUID") @PathVariable UUID userId, Principal principal) {
        return users.generateRecoveryCodes(userId, principal.getName());
    }

    /**
     * 发起 MFA 挑战。
     */
    @Operation(summary = "发起 MFA 挑战", description = "为指定用户和 MFA 因子创建挑战，短信/邮件/WebAuthn 原型会返回验证码。")
    @PostMapping("/{userId}/mfa-challenges")
    @ResponseStatus(HttpStatus.CREATED)
    MfaChallengeResponse startMfaChallenge(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "MFA 挑战发起请求") @Valid @RequestBody StartMfaChallengeRequest request,
        Principal principal
    ) {
        return users.startMfaChallenge(userId, request, principal.getName());
    }

    /**
     * 查询 MFA 挑战审计记录。
     */
    @Operation(summary = "查询 MFA 挑战记录", description = "按用户、因子、状态、类型和关键字过滤 MFA 挑战记录。")
    @GetMapping("/mfa-challenges")
    List<MfaChallengeDetailResponse> mfaChallenges(
        @Parameter(description = "用户 UUID") @RequestParam(required = false) UUID userId,
        @Parameter(description = "MFA 因子 UUID") @RequestParam(required = false) UUID factorId,
        @Parameter(description = "挑战状态") @RequestParam(required = false) MfaChallengeStatus status,
        @Parameter(description = "MFA 因子类型") @RequestParam(required = false) MfaFactorType type,
        @Parameter(description = "关键字，匹配 challengeId、用户名、显示名、因子名、类型或状态") @RequestParam(required = false) String keyword,
        @Parameter(description = "最大返回数量，默认 100，最大 500") @RequestParam(required = false) Integer limit
    ) {
        return users.listMfaChallenges(userId, factorId, status, type, keyword, limit);
    }

    /**
     * 查询单个 MFA 挑战记录详情。
     */
    @Operation(summary = "获取 MFA 挑战详情", description = "根据挑战记录 UUID 返回 MFA 挑战详情。")
    @GetMapping("/mfa-challenges/{challengeRecordId}")
    MfaChallengeDetailResponse mfaChallenge(@Parameter(description = "MFA 挑战记录 UUID") @PathVariable UUID challengeRecordId) {
        return users.getMfaChallenge(challengeRecordId);
    }

    /**
     * 校验 MFA 挑战验证码。
     */
    @Operation(summary = "校验 MFA 挑战", description = "校验 TOTP、恢复码、短信、邮件或 WebAuthn 原型验证码。")
    @PostMapping("/mfa-challenge-verifications")
    VerifyMfaChallengeResponse verifyMfaChallenge(
        @Parameter(description = "MFA 挑战校验请求") @Valid @RequestBody VerifyMfaChallengeRequest request,
        Principal principal
    ) {
        return users.verifyMfaChallenge(request, principal.getName());
    }
}
