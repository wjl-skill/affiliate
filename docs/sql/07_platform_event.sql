-- ===================================================================
-- 模块名称：platform-event (事件驱动总线与事务发件箱模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. event_outbox : 事务发件箱表 (Transactional Outbox)
--   2. audit_event  : 平台审计日志事件表
-- ===================================================================

-- 1. 事务发件箱持久化表
create table if not exists event_outbox (
    id uuid primary key,                            -- 领域事件全局唯一 UUID
    tenant_id varchar(64) not null default 'public',-- 产生事件的租户标识
    event_type varchar(160) not null,               -- 领域事件类型 (如 "creative.created.v1", "auction.win.v1")
    aggregate_id varchar(128) not null,             -- 聚合根业务主键
    payload jsonb not null,                         -- 事件完整载荷 JSONB 数据
    occurred_at timestamptz not null,               -- 事件发生时间戳
    published_at timestamptz,                       -- 中继成功投递至 Kafka 的发布时间戳
    attempts int not null default 0                 -- 投递重试尝试次数
);

comment on table event_outbox is '事务发件箱表（支撑本地事务与 Kafka 最终一致性）';
comment on column event_outbox.id is '事件 UUID';
comment on column event_outbox.tenant_id is '租户标识';
comment on column event_outbox.event_type is '事件类别契约';
comment on column event_outbox.aggregate_id is '聚合根 ID';
comment on column event_outbox.payload is 'JSONB 格式业务载荷';
comment on column event_outbox.occurred_at is '业务发生时间';
comment on column event_outbox.published_at is '投递成功时间（为空代表待中继）';
comment on column event_outbox.attempts is '失败重试次数计数';

-- 优化未发布事件的高并发轮询检索索引
create index if not exists ix_outbox_unpublished on event_outbox(published_at, occurred_at) where published_at is null;

-- 2. 平台审计日志事实表
create table if not exists audit_event (
    id uuid primary key,                            -- 审计日志 UUID
    tenant_id varchar(64) not null default 'public',-- 租户标识
    actor_id varchar(128),                          -- 操作者/调用方身份标识
    action varchar(128) not null,                   -- 触发的动作 (CREATE, UPDATE, DELETE, RETRY)
    resource_type varchar(64) not null,             -- 操作的资源类型 (CAMPAIGN, BUDGET, AD_SLOT)
    resource_id varchar(128),                       -- 操作的资源对象 ID
    payload jsonb not null default '{}'::jsonb,     -- 操作前后的上下文快照
    created_at timestamptz not null default now()   -- 操作记录时间戳
);

comment on table audit_event is '平台安全合规与操作审计流水表';
comment on column audit_event.id is '审计日志唯一主键';
comment on column audit_event.tenant_id is '租户标识';
comment on column audit_event.actor_id is '操作用户或微服务标识';
comment on column audit_event.action is '审计操作动作';
comment on column audit_event.resource_type is '目标资源类型';
comment on column audit_event.resource_id is '目标资源 ID';
comment on column audit_event.payload is '操作变更上下文 JSONB';
comment on column audit_event.created_at is '审计时间戳';

create index if not exists ix_audit_event_tenant_time on audit_event(tenant_id, created_at desc);
