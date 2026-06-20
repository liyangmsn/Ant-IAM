package com.antiam.service.identitysource;

import java.util.List;

public record DirectorySyncPayload(
    List<DirectoryOrganization> organizations,
    List<DirectoryUser> users,
    List<DirectoryGroup> groups
) {

    public DirectorySyncPayload {
        organizations = organizations == null ? List.of() : organizations;
        users = users == null ? List.of() : users;
        groups = groups == null ? List.of() : groups;
    }

    public static DirectorySyncPayload empty() {
        return new DirectorySyncPayload(List.of(), List.of(), List.of());
    }
}
