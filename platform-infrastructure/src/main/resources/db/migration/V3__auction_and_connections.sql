-- Flyway Migration V3: Auction Facts, Partner Connections & Billing Enhancements

-- 1. 竞价拍卖成交快照表
create table if not exists auction (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    request_id varchar(128) not null,
    ad_slot_id varchar(64) not null,
    creative_id varchar(64) not null,
    clearing_price numeric(19,6) not null check (clearing_price >= 0),
    currency char(3) not null default 'USD',
    advertiser varchar(128) not null,
    created_at timestamptz not null default now()
);
create index if not exists ix_auction_tenant_time on auction(tenant_id, created_at desc);
create index if not exists ix_auction_request on auction(request_id);

-- 2. 外部合作方连接配置表
create table if not exists partner_connection (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(200) not null,
    type varchar(32) not null,
    endpoint varchar(512) not null,
    settings jsonb not null default '{}'::jsonb,
    status varchar(32) not null default 'ACTIVE',
    updated_at timestamptz not null default now()
);
create index if not exists ix_partner_tenant_status on partner_connection(tenant_id, status);

-- 3. 增强 billing_entry 增加 direction 与 description
alter table if exists billing_entry add column if not exists direction varchar(16) not null default 'DEBIT';
alter table if exists billing_entry add column if not exists description varchar(256);
