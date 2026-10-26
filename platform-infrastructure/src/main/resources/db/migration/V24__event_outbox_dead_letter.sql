-- ===================================================================
-- V24: 事务发件箱死信队列与失败重放支持 (Transactional Outbox Dead Letter Queue & Replay)
-- ===================================================================

-- 1. 为现有发件箱表增加末次错误摘要字段
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS last_error text;

-- 2. 创建发件箱死信归档表 (超过最大重试次数时转移至此，解除热点阻塞并供人工/运维重放)
CREATE TABLE IF NOT EXISTS event_outbox_dead (
    id            uuid PRIMARY KEY,
    tenant_id     varchar(64) NOT NULL,
    event_type    varchar(160) NOT NULL,
    aggregate_id  varchar(128) NOT NULL,
    payload       jsonb NOT NULL,
    occurred_at   timestamptz NOT NULL,
    failed_at     timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts      int NOT NULL,
    last_error    text,
    replayed_at   timestamptz
);

-- 3. 死信表按租户与失败时间倒序索引
CREATE INDEX IF NOT EXISTS ix_outbox_dead_tenant ON event_outbox_dead(tenant_id, failed_at DESC);
