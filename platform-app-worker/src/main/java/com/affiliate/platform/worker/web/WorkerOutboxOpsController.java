package com.affiliate.platform.worker.web;

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
 * Worker 节点事务发件箱与死信重放运维接口 (Worker Outbox & Dead Letter Operations Controller)
 */
@RestController
@RequestMapping({"/api/v1/ops/outbox", "/api/v1/outbox"})
public class WorkerOutboxOpsController {

    private final OutboxDeadLetterService deadLetterService;

    public WorkerOutboxOpsController(OutboxDeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @GetMapping("/dead-letters")
    @PreAuthorize("@perm.has('ops:replay')")
    public List<EventOutboxDeadEntity> listDeadLetters(
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        String tenantId = TenantContext.get();
        return deadLetterService.getDeadLetters(tenantId, limit);
    }

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
        }
        return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILED",
                "message", "目标死信事件不存在或已被清理",
                "event_id", id
        ));
    }
}
