package com.antiam.dto;

import java.time.Instant;
import java.util.Map;

public final class FederationDtos {
    private FederationDtos() {
    }

    public record SamlMetadataResponse(String entityId, String ssoUrl, String certificateUse, String protocol) {
    }

    public record SamlAssertionResponse(
        String assertionId,
        String issuer,
        String audience,
        String acsUrl,
        String subject,
        Instant notBefore,
        Instant notOnOrAfter,
        Map<String, String> attributes
    ) {
    }

    public record CasLoginResponse(String redirectTo, String ticket, String service) {
    }

    public record CasServiceValidationResponse(
        boolean success,
        String user,
        String service,
        Map<String, String> attributes,
        String failureCode,
        String failureMessage
    ) {
    }
}
