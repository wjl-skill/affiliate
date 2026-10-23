package com.affiliate.platform.security.system;

import com.affiliate.platform.entity.AuditEventEntity;
import com.affiliate.platform.mapper.AuditEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 平台通用安全与权限变更审计服务 (Security Audit Event Service)
 * <p>
 * 对接 PostgreSQL `audit_event` 表 (`AuditEventMapper`)，具备双模持久化能力：
 * 1. 数据库在线时同步写入 `audit_event` 表并支持按租户查询；
 * 2. 数据库离线或单元测试环境下自动降级至内存环形队列。
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final int MAX_MEMORY_EVENTS = 500;

    private final AuditEventMapper auditEventMapper;
    private final ConcurrentLinkedDeque<AuditEventRecord> memoryEvents = new ConcurrentLinkedDeque<>();

    @Autowired
    public SecurityAuditService(@Autowired(required = false) AuditEventMapper auditEventMapper) {
        this.auditEventMapper = auditEventMapper;
    }

    public SecurityAuditService() {
        this(null);
    }

    @PostConstruct
    public void preloadFromDatabase() {
        if (auditEventMapper != null) {
            try {
                List<AuditEventEntity> rows = auditEventMapper.selectList(
                        new LambdaQueryWrapper<AuditEventEntity>()
                                .orderByDesc(AuditEventEntity::getCreatedAt)
                                .last("LIMIT 200")
                );
                if (rows != null && !rows.isEmpty()) {
                    for (AuditEventEntity entity : rows) {
                        memoryEvents.addLast(toRecord(entity));
                    }
                    log.info("[SecurityAudit] Preloaded {} audit events from PostgreSQL", rows.size());
                    return;
                }
            } catch (Exception e) {
                log.warn("[SecurityAudit] Database preload skipped, using in-memory fallback: {}", e.getMessage());
            }
        }
        seedDefaultAuditEvents();
    }

    private void seedDefaultAuditEvents() {
        if (!memoryEvents.isEmpty()) return;
        recordEvent("tenant-1", "admin", "SYSTEM_BOOT_VERIFY", "RBAC_ENGINE", "rbac-core", "{\"status\":\"VERIFIED\"}");
    }

    public AuditEventRecord recordEvent(
            String tenantId,
            String actorId,
            String action,
            String resourceType,
            String resourceId,
            String payload
    ) {
        UUID id = UUID.randomUUID();
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "tenant-1";
        String effectiveActor = (actorId != null && !actorId.isBlank()) ? actorId : "system";
        Instant now = Instant.now();

        AuditEventRecord record = new AuditEventRecord(
                id.toString(),
                effectiveTenant,
                effectiveActor,
                action,
                resourceType,
                resourceId,
                payload != null ? payload : "{}",
                now
        );

        memoryEvents.addFirst(record);
        while (memoryEvents.size() > MAX_MEMORY_EVENTS) {
            memoryEvents.pollLast();
        }

        if (auditEventMapper != null) {
            try {
                AuditEventEntity entity = new AuditEventEntity(
                        id,
                        effectiveTenant,
                        effectiveActor,
                        action,
                        resourceType,
                        resourceId,
                        payload != null ? payload : "{}",
                        now
                );
                auditEventMapper.insert(entity);
            } catch (Exception e) {
                log.debug("[SecurityAudit] Persist audit_event to DB skipped: {}", e.getMessage());
            }
        }
        return record;
    }

    public List<AuditEventRecord> listEvents(String tenantId, int limit) {
        int effectiveLimit = limit > 0 ? Math.min(limit, 200) : 50;
        if (auditEventMapper != null) {
            try {
                LambdaQueryWrapper<AuditEventEntity> wrapper = new LambdaQueryWrapper<>();
                if (tenantId != null && !tenantId.isBlank() && !"default".equals(tenantId)) {
                    wrapper.eq(AuditEventEntity::getTenantId, tenantId);
                }
                wrapper.orderByDesc(AuditEventEntity::getCreatedAt).last("LIMIT " + effectiveLimit);
                List<AuditEventEntity> entities = auditEventMapper.selectList(wrapper);
                if (entities != null && !entities.isEmpty()) {
                    return entities.stream().map(this::toRecord).toList();
                }
            } catch (Exception e) {
                log.debug("[SecurityAudit] Query audit_event from DB skipped: {}", e.getMessage());
            }
        }

        List<AuditEventRecord> result = new ArrayList<>();
        for (AuditEventRecord rec : memoryEvents) {
            if (tenantId == null || tenantId.isBlank() || "default".equals(tenantId) || tenantId.equals(rec.tenantId())) {
                result.add(rec);
                if (result.size() >= effectiveLimit) break;
            }
        }
        result.sort(Comparator.comparing(AuditEventRecord::createdAt).reversed());
        return result;
    }

    private AuditEventRecord toRecord(AuditEventEntity e) {
        return new AuditEventRecord(
                e.getId() != null ? e.getId().toString() : UUID.randomUUID().toString(),
                e.getTenantId(),
                e.getActorId(),
                e.getAction(),
                e.getResourceType(),
                e.getResourceId(),
                e.getPayload(),
                e.getCreatedAt() != null ? e.getCreatedAt() : Instant.now()
        );
    }

    public record AuditEventRecord(
            String id,
            String tenantId,
            String actorId,
            String action,
            String resourceType,
            String resourceId,
            String payload,
            Instant createdAt
    ) {}
}
