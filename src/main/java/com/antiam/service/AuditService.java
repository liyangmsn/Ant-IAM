package com.antiam.service;

import static com.antiam.dto.AuditDtos.AuditEventListResponse;
import static com.antiam.dto.AuditDtos.AuditEventResponse;

import com.antiam.common.NotFoundException;
import com.antiam.domain.AuditEvent;
import com.antiam.repository.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEvents;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    // 记录审计事件，使用独立事务尽量避免被业务事务回滚影响。
    public void record(String actor, String action, String targetType, String targetId, String detail) {
        auditEvents.save(new AuditEvent(actor, action, targetType, targetId, detail));
    }

    @Transactional(readOnly = true)
    // 查询单条审计事件详情。
    public AuditEventResponse get(java.util.UUID auditEventId) {
        AuditEvent event = auditEvents.findById(auditEventId)
            .orElseThrow(() -> new NotFoundException("Audit event not found: " + auditEventId));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    // 按操作者、动作、目标、时间范围和关键字搜索审计事件。
    public AuditEventListResponse search(
        String actor,
        String action,
        String targetType,
        String targetId,
        Instant from,
        Instant to,
        String keyword,
        int limit
    ) {
        int size = Math.clamp(limit, 1, 500);
        List<AuditEventResponse> resources = auditEvents
            .findAll(specification(actor, action, targetType, targetId, from, to, keyword), PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
            .stream()
            .map(this::toResponse)
            .toList();
        return new AuditEventListResponse(resources.size(), size, resources);
    }

    @Transactional(readOnly = true)
    // 兼容旧调用方式导出审计 CSV，不带关键字过滤。
    public String exportCsv(String actor, String action, String targetType, String targetId, Instant from, Instant to, int limit) {
        return exportCsv(actor, action, targetType, targetId, from, to, null, limit);
    }

    @Transactional(readOnly = true)
    // 按查询条件导出审计 CSV。
    public String exportCsv(String actor, String action, String targetType, String targetId, Instant from, Instant to, String keyword, int limit) {
        AuditEventListResponse response = search(actor, action, targetType, targetId, from, to, keyword, limit);
        StringBuilder csv = new StringBuilder("id,createdAt,actor,action,targetType,targetId,detail\n");
        response.resources().forEach(event -> csv
            .append(csv(event.id().toString())).append(',')
            .append(csv(event.createdAt().toString())).append(',')
            .append(csv(event.actor())).append(',')
            .append(csv(event.action())).append(',')
            .append(csv(event.targetType())).append(',')
            .append(csv(event.targetId())).append(',')
            .append(csv(event.detail()))
            .append('\n'));
        return csv.toString();
    }

    private Specification<AuditEvent> specification(
        String actor,
        String action,
        String targetType,
        String targetId,
        Instant from,
        Instant to,
        String keyword
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actor != null && !actor.isBlank()) {
                predicates.add(builder.equal(root.get("actor"), actor));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(builder.equal(root.get("targetType"), targetType));
            }
            if (targetId != null && !targetId.isBlank()) {
                predicates.add(builder.equal(root.get("targetId"), targetId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("actor")), pattern),
                    builder.like(builder.lower(root.get("action")), pattern),
                    builder.like(builder.lower(root.get("targetType")), pattern),
                    builder.like(builder.lower(root.get("targetId")), pattern),
                    builder.like(builder.lower(root.get("detail")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
            event.getId(),
            event.getActor(),
            event.getAction(),
            event.getTargetType(),
            event.getTargetId(),
            event.getDetail(),
            event.getCreatedAt());
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
