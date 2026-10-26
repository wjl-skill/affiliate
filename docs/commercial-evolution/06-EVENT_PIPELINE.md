# 事件管线(Phase 1)

> 所属:商业级演化详细设计(阶段 0–2)。总览见 [README](README.md)。


- **Outbox**:已有 `JdbcOutboxStore` + `OutboxRelayScheduler`。补充:
  - 行锁取批(`FOR UPDATE SKIP LOCKED`),多 Pod 并行无需分布式锁;
  - 指数退避 + `attempts`、`last_error`;超过阈值 → `outbox_dead` 表 + 告警;
  - 事件 schema 版本号 `schema_v`,向后兼容规则写入文档。
- **消费者**(如 `ConversionEventConsumer`):
  - 幂等:现有 Redis `SET NX 24h` 只是快速去重;**最终幂等依赖 DB 唯一键** `(offer_id, tx_id)`,Redis 过期/丢失不再产生重复。
  - 失败:`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`(重试 3 次,指数退避)→ `*.DLT`;提供 `DLT 重放` 管理接口(权限 `ops:replay`)。
  - `S2sPostbackService` 的"Kafka 失败 → 同步落库"降级保留,但需打指标 `postback_degraded_total` 并有上限保护,避免拖垮 Web 线程。
- Topic 规范:`<domain>.<entity>.<event>.v1`,key = `tenant_id:entity_id`(保证同实体有序)。

