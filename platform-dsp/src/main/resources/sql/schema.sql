-- ===================================================================
-- 模块名称：platform-dsp (需求方广告活动与排期投放模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. campaign : 广告活动主表（包含预算上限、出价上限与定向条件）
-- ===================================================================

create table if not exists campaign (
    id varchar(64) primary key,                     -- 广告活动唯一标识 (如 "campaign_summer_sale")
    tenant_id varchar(64) not null default 'public',-- 租户隔离标识
    advertiser_id varchar(128) not null,            -- 所属广告主客户标识
    name varchar(200) not null,                     -- 广告活动展示名称
    start_date date not null,                       -- 投放排期开始日期
    end_date date not null,                         -- 投放排期结束日期
    daily_budget numeric(19,6) not null check (daily_budget >= 0), -- 每日消耗限额 (USD)
    max_bid numeric(19,6) not null check (max_bid >= 0),           -- 单次出价保护上限 (CPM USD)
    target_domains jsonb not null default '[]'::jsonb,             -- 媒体域名定向白名单
    target_device_types jsonb not null default '[]'::jsonb,        -- 设备形态编码白名单
    status varchar(32) not null default 'DRAFT',    -- 活动状态 (DRAFT, ACTIVE, PAUSED, ENDED)
    created_at timestamptz not null default now(),  -- 创建时间戳
    constraint ck_campaign_dates check (end_date >= start_date)
);

comment on table campaign is '广告主投放计划与排期活动主表';
comment on column campaign.id is '活动唯一标识';
comment on column campaign.tenant_id is '所属租户';
comment on column campaign.advertiser_id is '广告主客户 ID';
comment on column campaign.name is '活动名称';
comment on column campaign.start_date is '排期开始日期';
comment on column campaign.end_date is '排期截止日期';
comment on column campaign.daily_budget is '每日预算上限';
comment on column campaign.max_bid is '最高限价出价 (Max Bid)';
comment on column campaign.target_domains is '目标定向域名 JSONB 数组';
comment on column campaign.target_device_types is '目标设备形态编码 JSONB 数组';
comment on column campaign.status is '活动状态机 (DRAFT, ACTIVE, PAUSED, ENDED)';
comment on column campaign.created_at is '创建时间戳';

create index if not exists ix_campaign_tenant_status on campaign(tenant_id, status);
create index if not exists ix_campaign_advertiser on campaign(tenant_id, advertiser_id);
create index if not exists ix_campaign_schedule on campaign(tenant_id, start_date, end_date) where status = 'ACTIVE';
