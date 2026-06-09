package com.antiam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class ScimDtos {
    private ScimDtos() {
    }

    public record ScimName(String formatted, String familyName, String givenName) {
    }

    public record ScimEmail(@NotBlank String value, String type, Boolean primary) {
    }

    public record ScimPhoneNumber(String value, String type, Boolean primary) {
    }

    public record CreateScimUserRequest(
        @NotBlank String userName,
        String displayName,
        @Valid ScimName name,
        List<@Valid ScimEmail> emails,
        List<@Valid ScimPhoneNumber> phoneNumbers,
        Boolean active
    ) {
    }

    public record ScimUserResponse(
        List<String> schemas,
        String id,
        String userName,
        String displayName,
        ScimName name,
        List<ScimEmail> emails,
        List<ScimPhoneNumber> phoneNumbers,
        Boolean active
    ) {
    }

    public record ScimMember(@NotBlank String value, String display, @JsonProperty("$ref") String ref, String type) {
    }

    public record CreateScimGroupRequest(@NotBlank String displayName, List<@Valid ScimMember> members) {
    }

    public record ScimGroupResponse(
        List<String> schemas,
        String id,
        String displayName,
        List<ScimMember> members
    ) {
    }

    public record CreateScimOrganizationRequest(
        @NotBlank String externalId,
        @NotBlank String displayName,
        UUID parentId
    ) {
    }

    public record ScimOrganizationResponse(
        List<String> schemas,
        String id,
        String externalId,
        String displayName,
        UUID parentId
    ) {
    }

    public record ScimListResponse<T>(
        List<String> schemas,
        int totalResults,
        int startIndex,
        int itemsPerPage,
        List<T> Resources
    ) {
    }

    public record ScimFeature(Boolean supported) {
    }

    public record ScimAuthenticationScheme(
        String type,
        String name,
        String description,
        String specUri,
        String documentationUri,
        Boolean primary
    ) {
    }

    public record ScimServiceProviderConfigResponse(
        List<String> schemas,
        String documentationUri,
        ScimFeature patch,
        ScimFeature bulk,
        ScimFeature filter,
        ScimFeature changePassword,
        ScimFeature sort,
        ScimFeature etag,
        List<ScimAuthenticationScheme> authenticationSchemes
    ) {
    }

    public record ScimResourceTypeResponse(
        List<String> schemas,
        String id,
        String name,
        String endpoint,
        String description,
        String schema
    ) {
    }

    public record ScimSchemaAttribute(String name, String type, Boolean multiValued, Boolean required, String mutability) {
    }

    public record ScimSchemaResponse(
        List<String> schemas,
        String id,
        String name,
        String description,
        List<ScimSchemaAttribute> attributes
    ) {
    }
}
