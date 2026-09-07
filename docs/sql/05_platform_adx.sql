-- ===================================================================
-- 模块名称：platform-adx (广告交易平台与实时竞价模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. auction : RTB 竞价拍卖成交事实记录表
-- ===================================================================

create table if not exists auction (
    id varchar(64) primary key,                     -- 拍卖唯一主键 ID (如 "auction_1750000000_1")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    request_id varchar(128) not null,               -- OpenRTB 竞价请求 ID
    ad_slot_id varchar(64) not null,                -- 竞价广告位 ID
    creative_id varchar(64) not null,               -- 中标投放的广告素材 ID
    clearing_price numeric(19,6) not null check (clearing_price >= 0), -- 结算成交价格 (USD)
    currency char(3) not null default 'USD',        -- 结算货币三位代码
    advertiser varchar(128) not null,               -- 中标广告主/活动标识
    created_at timestamptz not null default now()   -- 竞价成交时间戳
);

comment on table auction is 'RTB 实时竞价拍卖成交事实快照表';
comment on column auction.id is '拍卖唯一标识';
comment on column auction.tenant_id is '所属租户';
comment on column auction.request_id is 'OpenRTB 请求全局追踪 ID';
comment on column auction.ad_slot_id is '曝光广告位 ID';
comment on column auction.creative_id is '胜出物料 ID';
comment on column auction.clearing_price is '最终成交清算价格 (CPM USD)';
comment on column auction.currency is '结算货币';
comment on column auction.advertiser is '中标广告主标识';
comment on column auction.created_at is '成交时间戳';

create index if not exists ix_auction_tenant_time on auction(tenant_id, created_at desc);
create index if not exists ix_auction_request on auction(request_id);
create index if not exists ix_auction_slot on auction(tenant_id, ad_slot_id, created_at desc);
