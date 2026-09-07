-- ===================================================================
-- 模块名称：platform-budget (资金预算预占与频次控制模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. campaign_budget    : 活动主预算配置与可用余额表
--   2. budget_reservation : 实时竞价预算原子预占流水表
-- ===================================================================

-- 1. 活动主预算表
create table if not exists campaign_budget (
    id varchar(64) primary key,                     -- 主键 (如 "budget_tenant_campaign")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    campaign_id varchar(64) not null,               -- 广告活动标识
    total_budget numeric(19,6) not null default 0,  -- 总分配预算金额 (USD)
    daily_budget numeric(19,6) not null default 0,  -- 每日限额预算 (USD)
    balance numeric(19,6) not null default 0,       -- 实时可用剩余余额 (USD)
    updated_at timestamptz not null default now(),  -- 最后更新时间
    constraint uk_budget_tenant_campaign unique (tenant_id, campaign_id)
);

comment on table campaign_budget is '广告活动预算配置与余额主表';
comment on column campaign_budget.id is '预算配置主键';
comment on column campaign_budget.tenant_id is '租户标识';
comment on column campaign_budget.campaign_id is '活动标识';
comment on column campaign_budget.total_budget is '总预算上限';
comment on column campaign_budget.daily_budget is '每日预算上限';
comment on column campaign_budget.balance is '当前可用余额';
comment on column campaign_budget.updated_at is '余额最后更新时间';

-- 2. 实时竞价预占流水表 (支持 TTL 超时释放对账)
create table if not exists budget_reservation (
    id uuid primary key,                            -- 预占唯一流水 UUID
    tenant_id varchar(64) not null default 'public',-- 租户标识
    campaign_id varchar(64) not null,               -- 预占扣减的活动标识
    user_id varchar(128),                           -- 受众用户标识
    amount numeric(19,6) not null check (amount >= 0), -- 预占金额 (USD)
    status varchar(32) not null default 'RESERVED', -- 状态 (RESERVED 预占中, CONFIRMED 已转正, RELEASED 已归还)
    created_at timestamptz not null default now(),  -- 预占创建时间
    confirmed_at timestamptz,                       -- 确认成交时间
    expires_at timestamptz not null                 -- 预占超时失效时间（默认 120 秒）
);

comment on table budget_reservation is '实时竞价资金原子预占与对账流水表';
comment on column budget_reservation.id is '预占流水 UUID';
comment on column budget_reservation.tenant_id is '所属租户';
comment on column budget_reservation.campaign_id is '广告计划 ID';
comment on column budget_reservation.user_id is '受众用户 ID';
comment on column budget_reservation.amount is '预占金额';
comment on column budget_reservation.status is '预占生命周期状态';
comment on column budget_reservation.created_at is '预占发起时间';
comment on column budget_reservation.confirmed_at is '胜出转正确认时间';
comment on column budget_reservation.expires_at is '预占超时失效截止时间';

create index if not exists ix_reservation_tenant_campaign on budget_reservation(tenant_id, campaign_id, status);
create index if not exists ix_reservation_expiry on budget_reservation(status, expires_at);
