package com.antiam.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.antiam.dto.OAuthDtos.TokenResponse;
import com.antiam.dto.OAuthDtos.OidcDiscoveryResponse;
import com.antiam.dto.OAuthDtos.TokenIntrospectionResponse;
import com.antiam.dto.OAuthDtos.UserInfoResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OAuthDtosTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesTokenResponseWithOAuthFieldNames() {
        String json = mapper.writeValueAsString(new TokenResponse("access", "Bearer", 3600, "refresh", "id", "openid"));

        assertThat(json).isEqualTo("{\"access_token\":\"access\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"refresh\",\"id_token\":\"id\",\"scope\":\"openid\"}");
    }

    @Test
    void serializesOidcDiscoveryWithStandardFieldNames() {
        String json = mapper.writeValueAsString(new OidcDiscoveryResponse(
            "issuer",
            "authorization",
            "token",
            "userinfo",
            "introspection",
            "revocation",
            "jwks",
            java.util.List.of("code"),
            java.util.List.of("authorization_code"),
            java.util.List.of("public"),
            java.util.List.of("RS256"),
            java.util.List.of("openid"),
            java.util.List.of("S256"),
            java.util.List.of("client_secret_post"),
            java.util.Map.of("standard", java.util.List.of("sub"))));

        assertThat(json).contains("\"authorization_endpoint\":\"authorization\"");
        assertThat(json).contains("\"jwks_uri\":\"jwks\"");
        assertThat(json).doesNotContain("authorizationEndpoint");
    }

    @Test
    void serializesUserInfoWithStandardClaimNames() {
        String json = mapper.writeValueAsString(new UserInfoResponse("sub", "alice", "Alice", "alice@example.com", "13800000000"));

        assertThat(json).isEqualTo("{\"sub\":\"sub\",\"preferred_username\":\"alice\",\"name\":\"Alice\",\"email\":\"alice@example.com\",\"phone_number\":\"13800000000\"}");
    }

    @Test
    void serializesIntrospectionWithStandardFieldNames() {
        String json = mapper.writeValueAsString(new TokenIntrospectionResponse(true, "client", "alice", "sub", "Bearer", "openid", 123));

        assertThat(json).contains("\"client_id\":\"client\"");
        assertThat(json).contains("\"token_type\":\"Bearer\"");
        assertThat(json).doesNotContain("clientId");
    }
}
