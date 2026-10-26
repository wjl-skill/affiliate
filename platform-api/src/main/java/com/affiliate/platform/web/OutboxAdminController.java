package com.affiliate.platform.web;

import com.affiliate.platform.event.EventOutboxDeadEntity;
import com.affiliate.platform.event.OutboxDeadLetterService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 事务发件箱与消息总线运维控制面 (Outbox Operations & Dead Letter Controller)
 */
@RestController
@RequestMapping("/api/v1/ops/outbox")
public class OutboxAdminController {

    private final OutboxDeadLetterService deadLetterService;

    public OutboxAdminController(OutboxDeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    /**
     * 查询当前租户下的死信事件列表
     * GET /api/v1/ops/outbox/dead-letters?limit=50
     */
    @GetMapping("/dead-letters")
    @PreAuthorize("@perm.has('ops:replay')")
    public List<EventOutboxDeadEntity> listDeadLetters(
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        String tenantId = TenantContext.get();
        return deadLetterService.getDeadLetters(tenantId, limit);
    }

    /**
     * 重放指定死信事件进入待发送队列
     * POST /api/v1/ops/outbox/replay/{id}
     */
    @PostMapping("/replay/{id}")
    @PreAuthorize("@perm.has('ops:replay')")
    public ResponseEntity<Map<String, Object>> replayDeadLetter(@PathVariable("id") UUID id) {
        boolean success = deadLetterService.replay(id);
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "死信事件已成功放回发件箱 pending 列表",
                    "event_id", id
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", "目标死信事件不存在或已被清理",
                    "event_id", id
            ));
        }
    }
}
