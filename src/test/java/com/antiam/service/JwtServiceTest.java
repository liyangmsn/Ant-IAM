package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.common.SamlSignatures;
import com.antiam.common.TokenSupport;
import com.antiam.domain.JwtSigningKey;
import com.antiam.domain.UserAccount;
import com.antiam.mapper.JwtMapper;
import com.antiam.repository.JwtSigningKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

class JwtServiceTest {

    private final JwtSigningKeyRepository signingKeys = mock(JwtSigningKeyRepository.class);
    private final Map<String, JwtSigningKey> stored = new HashMap<>();
    private final JwtService service = new JwtService(
        signingKeys,
        new TokenSupport(),
        mock(JwtMapper.class),
        mock(AuditService.class),
        new ObjectMapper());

    @BeforeEach
    void setUp() {
        stored.clear();
        when(signingKeys.save(any(JwtSigningKey.class))).thenAnswer(invocation -> {
            JwtSigningKey key = invocation.getArgument(0);
            stored.put(key.getKeyId(), key);
            return key;
        });
        when(signingKeys.findFirstByRetiredAtIsNullOrderByActivatedAtDesc())
            .thenAnswer(invocation -> stored.values().stream().findFirst());
        when(signingKeys.findByKeyId(anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0, String.class))));
    }

    @Test
    void signsAndVerifiesTokenWithGeneratedKey() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String token = service.signToken(
            "https://iam.example.com",
            "user-1",
            "checkout-app",
            issuedAt,
            issuedAt.plusSeconds(600),
            Map.of("preferred_username", "alice"));

        JwtService.TokenVerification verification = service.verify(token);

        assertThat(verification.valid()).isTrue();
        assertThat(verification.failureCode()).isNull();
        assertThat(verification.issuer()).isEqualTo("https://iam.example.com");
        assertThat(verification.audience()).isEqualTo("checkout-app");
        assertThat(verification.subject()).isEqualTo("user-1");
        assertThat(verification.issuedAt()).isEqualTo(issuedAt);
        assertThat(verification.expiresAt()).isEqualTo(issuedAt.plusSeconds(600));
        assertThat(verification.keyId()).isNotBlank();
        assertThat(verification.claims()).containsEntry("preferred_username", "alice");
    }

    @Test
    void rejectsUnexpectedIssuerAndAudience() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = service.signToken(
            "https://iam.example.com", "user-1", "checkout-app", issuedAt, issuedAt.plusSeconds(600), Map.of());

        assertThat(service.verify(token, "https://iam.example.com", "checkout-app").valid()).isTrue();
        assertThat(service.verify(token, "https://evil.example.com", null).failureCode()).isEqualTo("INVALID_ISSUER");
        assertThat(service.verify(token, null, "other-app").failureCode()).isEqualTo("INVALID_AUDIENCE");
    }

    @Test
    void ignoresCustomClaimsThatOverrideReservedClaims() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = service.signToken(
            "https://iam.example.com", "user-1", "checkout-app", issuedAt, issuedAt.plusSeconds(600),
            Map.of("sub", "admin", "exp", "9999999999", "department", "sales"));

        JwtService.TokenVerification verification = service.verify(token);

        assertThat(verification.valid()).isTrue();
        assertThat(verification.subject()).isEqualTo("user-1");
        assertThat(verification.expiresAt()).isEqualTo(issuedAt.plusSeconds(600));
        assertThat(verification.claims()).containsEntry("department", "sales");
    }

    @Test
    void signsSamlAssertionWithVerifiableSelfSignedCertificate() throws Exception {
        JwtService.SigningMaterial material = service.activeSigningMaterial();
        X509Certificate certificate = material.certificate();
        certificate.verify(certificate.getPublicKey());
        certificate.checkValidity();
        assertThat(service.activeSigningMaterial().certificate()).isEqualTo(certificate);

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="_a1-response" Version="2.0">
              <saml:Issuer>https://iam.example.com</saml:Issuer>
              <saml:Assertion ID="_a1" Version="2.0">
                <saml:Issuer>https://iam.example.com</saml:Issuer>
                <saml:Subject><saml:NameID>alice</saml:NameID></saml:Subject>
              </saml:Assertion>
            </samlp:Response>
            """.strip();
        String signed = SamlSignatures.signAssertion(xml, "_a1", material.privateKey(), certificate);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(signed)));
        Element assertion = (Element) document.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Assertion").item(0);
        assertion.setIdAttribute("ID", true);
        Element signature = (Element) document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0);
        assertThat(signature.getParentNode()).isEqualTo(assertion);
        Node previous = signature.getPreviousSibling();
        while (previous.getNodeType() != Node.ELEMENT_NODE) {
            previous = previous.getPreviousSibling();
        }
        assertThat(previous.getLocalName()).isEqualTo("Issuer");

        DOMValidateContext context = new DOMValidateContext(certificate.getPublicKey(), signature);
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context)).isTrue();

        assertion.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "NameID").item(0).setTextContent("admin");
        DOMValidateContext tampered = new DOMValidateContext(certificate.getPublicKey(), signature);
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(tampered).validate(tampered)).isFalse();
    }

    @Test
    void reusesActiveKeyAcrossSignatures() {
        Instant now = Instant.now();

        String first = service.signToken("https://iam.example.com", "user-1", "checkout-app", now, now.plusSeconds(60), Map.of());
        String second = service.signToken("https://iam.example.com", "user-2", "checkout-app", now, now.plusSeconds(60), Map.of());

        assertThat(service.verify(first).keyId()).isEqualTo(service.verify(second).keyId());
        assertThat(stored).hasSize(1);
    }

    @Test
    void signsDistinctIdTokensWithinTheSameSecond() {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        Instant expiresAt = Instant.now().plusSeconds(600);

        String first = service.signIdToken("https://iam.example.com", user, "client-1", "openid", expiresAt, null, Set.of(), Map.of());
        String second = service.signIdToken("https://iam.example.com", user, "client-1", "openid", expiresAt, null, Set.of(), Map.of());

        assertThat(first).isNotEqualTo(second);
        assertThat(service.verify(first).claims()).containsKey("jti");
        assertThat(service.verify(second).claims()).containsKey("jti");
    }

    @Test
    void returnsAuthorizationNonceInIdToken() {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(UUID.randomUUID());

        String token = service.signIdToken(
            "https://iam.example.com",
            user,
            "client-1",
            "openid",
            Instant.now().plusSeconds(600),
            "client-nonce-1",
            Set.of(),
            Map.of());

        assertThat(service.verify(token).claims()).containsEntry("nonce", "client-nonce-1");
    }

    @Test
    void rejectsTamperedPayload() {
        Instant now = Instant.now();
        String token = service.signToken("https://iam.example.com", "user-1", "checkout-app", now, now.plusSeconds(60), Map.of());
        String[] parts = token.split("\\.");

        String forged = parts[0] + "." + segment("{\"iss\":\"https://iam.example.com\",\"sub\":\"attacker\",\"aud\":\"checkout-app\",\"exp\":9999999999}") + "." + parts[2];

        assertThat(service.verify(forged).failureCode()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    void rejectsUnsupportedAlgorithm() {
        String forged = segment("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + segment("{\"sub\":\"user-1\"}") + ".x";

        JwtService.TokenVerification verification = service.verify(forged);

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failureCode()).isEqualTo("UNSUPPORTED_ALGORITHM");
    }

    @Test
    void rejectsUnknownKeyId() {
        String forged = segment("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"missing\"}") + "." + segment("{\"sub\":\"user-1\"}") + ".x";

        JwtService.TokenVerification verification = service.verify(forged);

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failureCode()).isEqualTo("UNKNOWN_KEY");
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        String token = service.signToken(
            "https://iam.example.com",
            "user-1",
            "checkout-app",
            now.minusSeconds(1_200),
            now.minusSeconds(600),
            Map.of());

        JwtService.TokenVerification verification = service.verify(token);

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failureCode()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(service.verify("not-a-token").failureCode()).isEqualTo("MALFORMED_TOKEN");
        assertThat(service.verify("").failureCode()).isEqualTo("MALFORMED_TOKEN");
        assertThat(service.verify(null).failureCode()).isEqualTo("MALFORMED_TOKEN");
    }

    private static String segment(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
