package com.antiam.web;

import static com.antiam.dto.FederationDtos.CasLoginResponse;
import static com.antiam.dto.FederationDtos.CasServiceValidationResponse;
import static com.antiam.dto.FederationDtos.JwtSsoTokenResponse;
import static com.antiam.dto.FederationDtos.JwtSsoVerificationResponse;
import static com.antiam.dto.FederationDtos.SamlAssertionResponse;
import static com.antiam.dto.FederationDtos.SamlMetadataResponse;
import static com.antiam.dto.FederationDtos.VerifyJwtTokenRequest;

import com.antiam.config.IssuerResolver;
import com.antiam.service.FederationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@Tag(name = "联邦协议", description = "SAML 2.0、CAS 和 JWT 单点登录协议端点")
public class FederationController {

    private final FederationService federation;
    private final IssuerResolver issuerResolver;

    /**
     * 输出 SAML 元数据的 JSON 视图。
     */
    @Operation(summary = "获取 SAML 元数据", description = "返回身份提供方 SAML 元数据信息，便于管理界面查看和调试。")
    @GetMapping("/saml2/metadata")
    SamlMetadataResponse samlMetadata(HttpServletRequest request) {
        return federation.samlMetadata(issuerResolver.resolve(request));
    }

    /**
     * 输出 SAML 标准 XML 元数据。
     */
    @Operation(summary = "获取 SAML XML 元数据", description = "返回可被服务提供方导入的 SAML 2.0 元数据 XML。")
    @GetMapping(value = "/saml2/metadata.xml", produces = MediaType.APPLICATION_XML_VALUE)
    String samlMetadataXml(HttpServletRequest request) {
        return federation.samlMetadataXml(issuerResolver.resolve(request));
    }

    /**
     * 为当前用户签发 SAML 断言。
     */
    @Operation(summary = "签发 SAML 断言", description = "根据服务提供方 entity_id 为当前用户签发 SAML 断言。")
    @GetMapping("/saml2/sso")
    SamlAssertionResponse samlSso(
        @Parameter(description = "服务提供方 Entity ID") @RequestParam("entity_id") String entityId,
        HttpServletRequest request,
        Principal principal
    ) {
        String issuer = issuerResolver.resolve(request);
        return federation.issueSamlAssertion(entityId, principal.getName(), issuer);
    }

    /**
     * 为当前用户签发 SAML Response XML。
     */
    @Operation(summary = "签发 SAML Response XML", description = "根据服务提供方 entity_id 返回 SAML Response XML。")
    @GetMapping(value = "/saml2/sso/xml", produces = MediaType.APPLICATION_XML_VALUE)
    String samlSsoXml(
        @Parameter(description = "服务提供方 Entity ID") @RequestParam("entity_id") String entityId,
        HttpServletRequest request,
        Principal principal
    ) {
        String issuer = issuerResolver.resolve(request);
        return federation.issueSamlResponseXml(entityId, principal.getName(), issuer);
    }

    /**
     * 为当前用户签发 JWT 单点登录令牌。
     */
    @Operation(summary = "签发 JWT 单点登录令牌", description = "按应用 SSO 配置的 JWT Audience（或 client_id）为当前登录用户签发 RS256 令牌，有效期取 access_token 配置。")
    @GetMapping("/jwt/sso")
    JwtSsoTokenResponse jwtSso(
        @Parameter(description = "应用 JWT Audience，也接受 client_id") @RequestParam("audience") String audience,
        HttpServletRequest request,
        Principal principal
    ) {
        String issuer = issuerResolver.resolve(request);
        return federation.issueJwtSsoToken(audience, principal.getName(), issuer);
    }

    /**
     * 校验 JWT 令牌签名与有效期。
     */
    @Operation(summary = "校验 JWT 令牌", description = "使用服务端签名密钥按 kid 校验 RS256 签名与有效期，返回令牌声明；校验失败时以 failureCode 说明原因。")
    @PostMapping("/jwt/verify")
    JwtSsoVerificationResponse verifyJwtToken(@Parameter(description = "JWT 校验请求") @Valid @RequestBody VerifyJwtTokenRequest request) {
        String expectedIssuer = request.issuer() != null && !request.issuer().isBlank()
            ? request.issuer()
            : issuerResolver.configuredIssuer();
        return federation.verifyJwtSsoToken(request.token(), expectedIssuer, request.audience());
    }

    /**
     * CAS 登录端点：携带会话令牌时签发服务票据；浏览器直接访问时跳转到登录页完成认证。
     */
    @Operation(summary = "CAS 登录", description = "携带 Bearer 会话令牌时为当前用户和目标 service 签发 CAS Service Ticket；未登录的浏览器请求会 302 跳转到前端 CAS 登录页。")
    @GetMapping("/cas/login")
    ResponseEntity<CasLoginResponse> casLogin(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam String service,
        Principal principal
    ) {
        if (principal == null) {
            return redirect("/sso/cas/login", service);
        }
        return ResponseEntity.ok(federation.issueCasTicket(service, principal.getName()));
    }

    /**
     * CAS 登出端点：跳转到前端登出页结束当前会话，再返回已注册的 service 地址。
     */
    @Operation(summary = "CAS 登出", description = "302 跳转到前端登出页，结束当前浏览器会话；service 为已注册 CAS 应用时登出后跳回该地址。")
    @GetMapping("/cas/logout")
    ResponseEntity<Void> casLogout(@Parameter(description = "登出后返回的 service 地址") @RequestParam(required = false) String service) {
        return redirect("/sso/logout", service);
    }

    /**
     * CAS 1.0 票据校验，返回 yes/no 纯文本。
     */
    @Operation(summary = "CAS 1.0 票据校验", description = "以 CAS 1.0 纯文本格式返回校验结果：成功为 yes 和用户名两行，失败为 no。")
    @GetMapping(value = "/cas/validate", produces = MediaType.TEXT_PLAIN_VALUE)
    String casValidate(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam(required = false) String service,
        @Parameter(description = "CAS Service Ticket") @RequestParam(required = false) String ticket
    ) {
        CasServiceValidationResponse response = federation.validateCasTicket(service, ticket);
        return response.success() ? "yes\n" + response.user() + "\n" : "no\n\n";
    }

    /**
     * 校验 CAS 票据（proxyValidate）；本服务不签发代理票据，按服务票据校验。
     */
    @Operation(summary = "CAS 代理票据校验", description = "兼容 CAS proxyValidate；当前不签发代理票据（PT），按 Service Ticket 校验并返回 JSON 结果。")
    @GetMapping("/cas/proxyValidate")
    CasServiceValidationResponse casProxyValidate(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam(required = false) String service,
        @Parameter(description = "CAS Service Ticket") @RequestParam(required = false) String ticket
    ) {
        return federation.validateCasTicket(service, ticket);
    }

    /**
     * 以 CAS 3.0 XML 格式校验 CAS 票据（proxyValidate）。
     */
    @Operation(summary = "CAS XML 代理票据校验", description = "兼容 CAS p3/proxyValidate；按 Service Ticket 校验并返回 CAS 3.0 XML。")
    @GetMapping(value = "/cas/p3/proxyValidate", produces = MediaType.APPLICATION_XML_VALUE)
    String casProxyValidateXml(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam(required = false) String service,
        @Parameter(description = "CAS Service Ticket") @RequestParam(required = false) String ticket
    ) {
        return federation.validateCasTicketXml(service, ticket);
    }

    /**
     * 校验 CAS 服务票据。
     */
    @Operation(summary = "CAS 票据校验", description = "校验 service 与 ticket 是否匹配，并返回用户信息。")
    @GetMapping("/cas/serviceValidate")
    CasServiceValidationResponse casServiceValidate(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam(required = false) String service,
        @Parameter(description = "CAS Service Ticket") @RequestParam(required = false) String ticket
    ) {
        return federation.validateCasTicket(service, ticket);
    }

    /**
     * 以 XML 格式校验 CAS 服务票据。
     */
    @Operation(summary = "CAS XML 票据校验", description = "以 CAS p3/serviceValidate XML 格式返回票据校验结果。")
    @GetMapping(value = "/cas/p3/serviceValidate", produces = MediaType.APPLICATION_XML_VALUE)
    String casServiceValidateXml(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam(required = false) String service,
        @Parameter(description = "CAS Service Ticket") @RequestParam(required = false) String ticket
    ) {
        return federation.validateCasTicketXml(service, ticket);
    }

    private static <T> ResponseEntity<T> redirect(String path, String service) {
        UriComponentsBuilder target = UriComponentsBuilder.fromPath(path);
        if (service != null && !service.isBlank()) {
            target.queryParam("service", service);
        }
        URI location = URI.create(target.encode(StandardCharsets.UTF_8).build().toUriString());
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
