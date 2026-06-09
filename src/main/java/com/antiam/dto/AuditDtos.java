package com.antiam.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditEventResponse(
        UUID id,
        String actor,
        String action,
        String targetType,
        String targetId,
        String detail,
        Instant createdAt
    ) {
    }

    public record AuditEventListResponse(int totalResults, int limit, List<AuditEventResponse> resources) {
    }
}
