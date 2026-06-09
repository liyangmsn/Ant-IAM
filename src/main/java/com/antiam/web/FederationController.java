package com.antiam.web;

import static com.antiam.dto.FederationDtos.CasLoginResponse;
import static com.antiam.dto.FederationDtos.CasServiceValidationResponse;
import static com.antiam.dto.FederationDtos.SamlAssertionResponse;
import static com.antiam.dto.FederationDtos.SamlMetadataResponse;

import com.antiam.service.FederationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "联邦协议", description = "SAML 2.0 和 CAS 协议端点")
public class FederationController {

    private final FederationService federation;

    /**
     * 输出 SAML 元数据的 JSON 视图。
     */
    @Operation(summary = "获取 SAML 元数据", description = "返回身份提供方 SAML 元数据信息，便于管理界面查看和调试。")
    @GetMapping("/saml2/metadata")
    SamlMetadataResponse samlMetadata(HttpServletRequest request) {
        return federation.samlMetadata(request.getRequestURL().toString().replace("/saml2/metadata", ""));
    }

    /**
     * 输出 SAML 标准 XML 元数据。
     */
    @Operation(summary = "获取 SAML XML 元数据", description = "返回可被服务提供方导入的 SAML 2.0 元数据 XML。")
    @GetMapping(value = "/saml2/metadata.xml", produces = MediaType.APPLICATION_XML_VALUE)
    String samlMetadataXml(HttpServletRequest request) {
        return federation.samlMetadataXml(request.getRequestURL().toString().replace("/saml2/metadata.xml", ""));
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
        String issuer = request.getRequestURL().toString().replace("/saml2/sso", "");
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
        String issuer = request.getRequestURL().toString().replace("/saml2/sso/xml", "");
        return federation.issueSamlResponseXml(entityId, principal.getName(), issuer);
    }

    /**
     * CAS 登录端点，为当前用户签发服务票据。
     */
    @Operation(summary = "CAS 登录", description = "为当前用户和目标 service 签发 CAS Service Ticket。")
    @GetMapping("/cas/login")
    CasLoginResponse casLogin(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam String service,
        Principal principal
    ) {
        return federation.issueCasTicket(service, principal.getName());
    }

    /**
     * 校验 CAS 服务票据。
     */
    @Operation(summary = "CAS 票据校验", description = "校验 service 与 ticket 是否匹配，并返回用户信息。")
    @GetMapping("/cas/serviceValidate")
    CasServiceValidationResponse casServiceValidate(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam String service,
        @Parameter(description = "CAS Service Ticket") @RequestParam String ticket
    ) {
        return federation.validateCasTicket(service, ticket);
    }

    /**
     * 以 XML 格式校验 CAS 服务票据。
     */
    @Operation(summary = "CAS XML 票据校验", description = "以 CAS p3/serviceValidate XML 格式返回票据校验结果。")
    @GetMapping(value = "/cas/p3/serviceValidate", produces = MediaType.APPLICATION_XML_VALUE)
    String casServiceValidateXml(
        @Parameter(description = "CAS 客户端 service 地址") @RequestParam String service,
        @Parameter(description = "CAS Service Ticket") @RequestParam String ticket
    ) {
        return federation.validateCasTicketXml(service, ticket);
    }
}
