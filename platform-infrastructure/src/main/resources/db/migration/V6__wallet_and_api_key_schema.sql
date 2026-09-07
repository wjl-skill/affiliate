-- ===================================================================
-- Flyway Migration V6: 钱包账户与 API Key 凭证表 (Wallet & API Key Schema)
-- ===================================================================

create table if not exists wallet_account (
    account_id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    cash_balance numeric(14,4) not null default 0.0000,
    credit_limit numeric(14,4) not null default 0.0000,
    frozen_amount numeric(14,4) not null default 0.0000,
    currency varchar(8) not null default 'USD',
    updated_at timestamptz not null default now()
);
comment on table wallet_account is '广告主与渠道客多币种资金钱包账户表';
create index if not exists ix_wallet_tenant on wallet_account(tenant_id);

create table if not exists api_key (
    key_id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    secret varchar(128) not null,
    active boolean not null default true,
    created_at timestamptz not null default now()
);
comment on table api_key is '开放平台 B2B 机构对接 API Key 与 HMAC 密钥表';
create index if not exists ix_api_key_tenant on api_key(tenant_id, active);
