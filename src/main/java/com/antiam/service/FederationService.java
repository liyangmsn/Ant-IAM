package com.antiam.service;

import static com.antiam.dto.FederationDtos.CasLoginResponse;
import static com.antiam.dto.FederationDtos.CasServiceValidationResponse;
import static com.antiam.dto.FederationDtos.JwtSsoTokenResponse;
import static com.antiam.dto.FederationDtos.JwtSsoVerificationResponse;
import static com.antiam.dto.FederationDtos.SamlAssertionResponse;
import static com.antiam.dto.FederationDtos.SamlMetadataResponse;

import com.antiam.common.NotFoundException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.ApplicationSsoConfig;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.CasServiceTicket;
import com.antiam.domain.SamlAssertion;
import com.antiam.domain.UserAccount;
import com.antiam.repository.ApplicationSsoConfigRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.CasServiceTicketRepository;
import com.antiam.repository.SamlAssertionRepository;
import com.antiam.repository.UserAccountRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FederationService {

    private static final Duration SAML_ASSERTION_TTL = Duration.ofMinutes(5);
    private static final Duration CAS_TICKET_TTL = Duration.ofMinutes(5);
    private static final List<String> JWT_DEFAULT_CLAIMS = List.of(
        "preferred_username", "name", "email", "phone_number", "tenant_id", "organization_id");

    private final ApplicationSsoConfigRepository ssoConfigs;
    private final SamlAssertionRepository samlAssertions;
    private final CasServiceTicketRepository casTickets;
    private final UserAccountRepository users;
    private final AuthenticationEventRepository authenticationEvents;
    private final TokenSupport tokens;
    private final JwtService jwtService;

    // 生成 SAML 身份提供方元数据的结构化视图。
    public SamlMetadataResponse samlMetadata(String issuer) {
        return new SamlMetadataResponse(issuer, issuer + "/saml2/sso", "signing", "SAML2.0");
    }

    // 生成可供服务提供方导入的 SAML 元数据 XML。
    public String samlMetadataXml(String issuer) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="%s">
              <md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified</md:NameIDFormat>
                <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="%s/saml2/sso/xml"/>
              </md:IDPSSODescriptor>
            </md:EntityDescriptor>
            """.formatted(xml(issuer), xml(issuer));
    }

    @Transactional
    // 根据服务提供方 Entity ID 为当前用户签发短期 SAML 断言。
    public SamlAssertionResponse issueSamlAssertion(String entityId, String username, String issuer) {
        ApplicationSsoConfig config = ssoConfigs.findBySamlEntityId(entityId)
            .orElseThrow(() -> new NotFoundException("SAML service provider not found: " + entityId));
        if (!config.isEnabled() || config.getProtocol() != ApplicationProtocol.SAML2) {
            throw new IllegalArgumentException("Application is not configured for SAML2");
        }
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        Instant now = Instant.now();
        String assertionId = "_" + tokens.generateToken(24);
        Map<String, String> attributes = userAttributes(user);
        SamlAssertion saved = samlAssertions.save(new SamlAssertion(
            assertionId,
            config.getApplication(),
            user,
            issuer,
            entityId,
            config.getSamlAcsUrl(),
            now,
            now.plus(SAML_ASSERTION_TTL),
            joinAttributes(attributes)));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            config.getApplication(),
            AuthenticationEventType.TOKEN_ISSUED,
            null,
            null,
            "saml_assertion=" + saved.getAssertionId()));
        return new SamlAssertionResponse(
            saved.getAssertionId(),
            issuer,
            entityId,
            config.getSamlAcsUrl(),
            user.getUsername(),
            saved.getNotBefore(),
            saved.getNotOnOrAfter(),
            attributes);
    }

    @Transactional
    // 将签发的 SAML 断言包装为标准 SAML Response XML。
    public String issueSamlResponseXml(String entityId, String username, String issuer) {
        SamlAssertionResponse assertion = issueSamlAssertion(entityId, username, issuer);
        String attributes = assertion.attributes().entrySet().stream()
            .map(entry -> """
                    <saml:Attribute Name="%s">
                      <saml:AttributeValue>%s</saml:AttributeValue>
                    </saml:Attribute>
                """.formatted(xml(entry.getKey()), xml(entry.getValue())))
            .collect(java.util.stream.Collectors.joining());
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                            ID="%s-response"
                            Version="2.0"
                            IssueInstant="%s"
                            Destination="%s">
              <saml:Issuer>%s</saml:Issuer>
              <samlp:Status>
                <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
              </samlp:Status>
              <saml:Assertion ID="%s" Version="2.0" IssueInstant="%s">
                <saml:Issuer>%s</saml:Issuer>
                <saml:Subject>
                  <saml:NameID>%s</saml:NameID>
                  <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
                    <saml:SubjectConfirmationData NotOnOrAfter="%s" Recipient="%s"/>
                  </saml:SubjectConfirmation>
                </saml:Subject>
                <saml:Conditions NotBefore="%s" NotOnOrAfter="%s">
                  <saml:AudienceRestriction>
                    <saml:Audience>%s</saml:Audience>
                  </saml:AudienceRestriction>
                </saml:Conditions>
                <saml:AttributeStatement>
            %s
                </saml:AttributeStatement>
              </saml:Assertion>
            </samlp:Response>
            """.formatted(
            xml(assertion.assertionId()),
            Instant.now(),
            xml(assertion.acsUrl()),
            xml(assertion.issuer()),
            xml(assertion.assertionId()),
            Instant.now(),
            xml(assertion.issuer()),
            xml(assertion.subject()),
            assertion.notOnOrAfter(),
            xml(assertion.acsUrl()),
            assertion.notBefore(),
            assertion.notOnOrAfter(),
            xml(assertion.audience()),
            attributes);
    }

    @Transactional
    // 为当前用户和 CAS service 签发一次性服务票据。
    public CasLoginResponse issueCasTicket(String service, String username) {
        ApplicationSsoConfig config = ssoConfigs.findByCasServiceUrl(service)
            .orElseThrow(() -> new NotFoundException("CAS service not found: " + service));
        if (!config.isEnabled() || config.getProtocol() != ApplicationProtocol.CAS) {
            throw new IllegalArgumentException("Application is not configured for CAS");
        }
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        String ticket = "ST-" + tokens.generateToken(32);
        casTickets.save(new CasServiceTicket(
            tokens.sha256(ticket),
            config.getApplication(),
            user,
            service,
            Instant.now().plus(CAS_TICKET_TTL)));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            config.getApplication(),
            AuthenticationEventType.TOKEN_ISSUED,
            null,
            null,
            "cas_service=" + service));
        return new CasLoginResponse(buildRedirect(service, ticket), ticket, service);
    }

    @Transactional
    // 校验 CAS 服务票据并消费票据，成功后返回用户属性。
    public CasServiceValidationResponse validateCasTicket(String service, String ticket) {
        CasServiceTicket stored = casTickets.findByTicketHash(tokens.sha256(ticket))
            .orElse(null);
        if (stored == null) {
            return failure(service, "INVALID_TICKET", "Ticket not found");
        }
        if (!stored.getServiceUrl().equals(service)) {
            return failure(service, "INVALID_SERVICE", "Ticket is not valid for this service");
        }
        if (!stored.isUsable(Instant.now())) {
            return failure(service, "INVALID_TICKET", "Ticket is expired or already consumed");
        }
        stored.consume();
        UserAccount user = stored.getUser();
        return new CasServiceValidationResponse(true, user.getUsername(), service, userAttributes(user), null, null);
    }

    @Transactional
    // 以 CAS XML 响应格式返回票据校验结果。
    public String validateCasTicketXml(String service, String ticket) {
        CasServiceValidationResponse response = validateCasTicket(service, ticket);
        if (!response.success()) {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                  <cas:authenticationFailure code="%s">%s</cas:authenticationFailure>
                </cas:serviceResponse>
                """.formatted(xml(response.failureCode()), xml(response.failureMessage()));
        }
        String attributes = response.attributes().entrySet().stream()
            .map(entry -> "<cas:%s>%s</cas:%s>".formatted(xmlName(entry.getKey()), xml(entry.getValue()), xmlName(entry.getKey())))
            .collect(java.util.stream.Collectors.joining("\n        "));
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
              <cas:authenticationSuccess>
                <cas:user>%s</cas:user>
                <cas:attributes>
                  %s
                </cas:attributes>
              </cas:authenticationSuccess>
            </cas:serviceResponse>
            """.formatted(xml(response.user()), attributes);
    }

    @Transactional
    // 按 audience（或 client_id）为当前用户签发 JWT 单点登录令牌，有效期取应用 access_token 配置。
    public JwtSsoTokenResponse issueJwtSsoToken(String audience, String username, String issuer) {
        ApplicationSsoConfig config = jwtSsoConfig(audience);
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(Duration.ofMinutes(Math.max(1, config.getAccessTokenTtlMinutes())));
        String resolvedAudience = jwtAudience(config);
        String token = jwtService.signToken(
            issuer,
            user.getId().toString(),
            resolvedAudience,
            issuedAt,
            expiresAt,
            jwtClaims(config, user));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            config.getApplication(),
            AuthenticationEventType.TOKEN_ISSUED,
            null,
            null,
            "jwt_sso_audience=" + resolvedAudience));
        return new JwtSsoTokenResponse(
            token,
            "Bearer",
            issuer,
            resolvedAudience,
            user.getUsername(),
            issuedAt,
            expiresAt);
    }

    @Transactional(readOnly = true)
    // 校验 JWT 签名与有效期，校验失败时通过 failureCode 说明原因而不抛异常。
    public JwtSsoVerificationResponse verifyJwtSsoToken(String token) {
        JwtService.TokenVerification verification = jwtService.verify(token);
        return new JwtSsoVerificationResponse(
            verification.valid(),
            verification.keyId(),
            verification.issuer(),
            verification.audience(),
            verification.subject(),
            verification.issuedAt(),
            verification.expiresAt(),
            verification.claims(),
            verification.failureCode(),
            verification.failureMessage());
    }

    private ApplicationSsoConfig jwtSsoConfig(String audience) {
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT audience or client_id is required");
        }
        ApplicationSsoConfig config = ssoConfigs.findByJwtAudience(audience)
            .or(() -> ssoConfigs.findByClientId(audience))
            .orElseThrow(() -> new NotFoundException("JWT single sign-on client not found: " + audience));
        if (!config.isEnabled() || config.getProtocol() != ApplicationProtocol.JWT) {
            throw new IllegalArgumentException("Application is not configured for JWT single sign-on");
        }
        return config;
    }

    private String jwtAudience(ApplicationSsoConfig config) {
        if (config.getJwtAudience() != null && !config.getJwtAudience().isBlank()) {
            return config.getJwtAudience();
        }
        if (config.getClientId() != null && !config.getClientId().isBlank()) {
            return config.getClientId();
        }
        return config.getApplication().getCode();
    }

    private Map<String, String> jwtClaims(ApplicationSsoConfig config, UserAccount user) {
        Set<String> configured = splitValues(config.getIdTokenClaims());
        Collection<String> names = configured.isEmpty() ? JWT_DEFAULT_CLAIMS : configured;
        Map<String, String> claims = new LinkedHashMap<>();
        names.forEach(name -> {
            String value = claimValue(name, user);
            if (value != null && !value.isBlank()) {
                claims.put(name, value);
            }
        });
        splitEntries(config.getCustomClaims()).forEach((name, value) -> claims.put(name, value == null ? "" : value));
        return claims;
    }

    private String claimValue(String claim, UserAccount user) {
        return switch (claim) {
            case "preferred_username" -> user.getUsername();
            case "name" -> user.getDisplayName();
            case "email" -> user.getEmail();
            case "phone_number" -> user.getMobile();
            case "tenant_id" -> user.getTenant() == null ? null : user.getTenant().getId().toString();
            case "organization_id" -> user.getOrganization() == null ? null : user.getOrganization().getId().toString();
            default -> null;
        };
    }

    private Set<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split("\\R"))
            .filter(item -> !item.isBlank())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
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
                LinkedHashMap::new));
    }

    private CasServiceValidationResponse failure(String service, String code, String message) {
        return new CasServiceValidationResponse(false, null, service, Map.of(), code, message);
    }

    private Map<String, String> userAttributes(UserAccount user) {
        return new java.util.LinkedHashMap<>(Map.of(
            "displayName", nullToEmpty(user.getDisplayName()),
            "email", nullToEmpty(user.getEmail()),
            "mobile", nullToEmpty(user.getMobile())));
    }

    private String joinAttributes(Map<String, String> attributes) {
        return attributes.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String buildRedirect(String service, String ticket) {
        String separator = service.contains("?") ? "&" : "?";
        return service + separator + "ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private String xmlName(String value) {
        String normalized = value == null ? "value" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (normalized.isBlank() || !Character.isLetter(normalized.charAt(0))) {
            return "attr_" + normalized;
        }
        return normalized;
    }
}
