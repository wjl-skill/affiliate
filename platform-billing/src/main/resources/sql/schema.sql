-- ===================================================================
-- 模块名称：platform-billing (财务清算与复式记账账本模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. billing_account : 广告主与媒体客户资金账户主表
--   2. billing_entry   : 不可变复式记账交易分录流水表 (具备幂等键防护)
-- ===================================================================

-- 1. 客户资金账户主表
create table if not exists billing_account (
    id varchar(64) primary key,                     -- 账户唯一 ID (如 "acc_adv_1001")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    account_type varchar(32) not null,              -- 账户性质 (ADVERTISER, PUBLISHER, PLATFORM)
    balance numeric(19,6) not null default 0,       -- 实时账户余额 (USD)
    currency char(3) not null default 'USD',        -- 结算货币
    status varchar(32) not null default 'ACTIVE',    -- 账户状态 (ACTIVE 正常, FROZEN 冻结)
    created_at timestamptz not null default now(),  -- 开户时间
    updated_at timestamptz not null default now()   -- 更新时间
);

comment on table billing_account is '财务清算资金账户主表';
comment on column billing_account.id is '账户主键';
comment on column billing_account.tenant_id is '所属租户';
comment on column billing_account.account_type is '账户性质 (ADVERTISER 广告主, PUBLISHER 媒体, PLATFORM 平台)';
comment on column billing_account.balance is '账户资金可用余额';
comment on column billing_account.currency is '结算币种';
comment on column billing_account.status is '账户状态';

create index if not exists ix_billing_account_tenant on billing_account(tenant_id, account_type);

-- 2. 复式记账流水明细分录表 (不可变追加写)
create table if not exists billing_entry (
    id uuid primary key,                            -- 分录全局唯一 UUID
    tenant_id varchar(64) not null default 'public',-- 租户空间
    account_id varchar(128) not null,               -- 借贷发生账户 ID
    auction_id varchar(128),                        -- 关联竞价拍卖交易 ID（可选）
    entry_type varchar(32) not null,                -- 交易类型 (ADVERTISER_CHARGE, PUBLISHER_REVENUE, PLATFORM_FEE, REFUND)
    direction varchar(16) not null default 'DEBIT', -- 记账借贷方向 (DEBIT 借记, CREDIT 贷记)
    amount numeric(19,6) not null check (amount >= 0), -- 交易金额 (USD，非负数)
    currency char(3) not null default 'USD',        -- 结算币种
    idempotency_key varchar(256) not null unique,   -- 全局唯一防重幂等键 (强唯一索引)
    description varchar(256),                       -- 交易业务说明
    occurred_at timestamptz not null                -- 记账发生时间戳
);

comment on table billing_entry is '复式记账不可变会计凭证分录表';
comment on column billing_entry.id is '分录流水主键';
comment on column billing_entry.tenant_id is '租户标识';
comment on column billing_entry.account_id is '关联扣费/结算账户';
comment on column billing_entry.auction_id is '关联撮合拍卖交易 ID';
comment on column billing_entry.entry_type is '分录业务类型';
comment on column billing_entry.direction is '复式借贷方向 (DEBIT 借, CREDIT 贷)';
comment on column billing_entry.amount is '交易入账金额';
comment on column billing_entry.currency is '货币代码';
comment on column billing_entry.idempotency_key is '全局唯一幂等键 (防重复扣划)';
comment on column billing_entry.description is '交易说明';
comment on column billing_entry.occurred_at is '发生时间戳';

create index if not exists ix_billing_tenant_time on billing_entry(tenant_id, occurred_at desc);
create index if not exists ix_billing_account_time on billing_entry(tenant_id, account_id, occurred_at desc);
create index if not exists ix_billing_auction on billing_entry(auction_id) where auction_id is not null;
