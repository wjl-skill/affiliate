-- ===================================================================
-- 模块名称：platform-reporting (广告效果统计与多维报表分析模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. report_daily  : 多维聚合日报表 (曝光/点击/转化/消耗/营收)
--   2. report_hourly : 小时级实时效果趋势表
-- ===================================================================

-- 1. 多维日报表
create table if not exists report_daily (
    tenant_id varchar(64) not null default 'public',-- 租户标识
    report_date date not null,                      -- 统计日期 (YYYY-MM-DD)
    campaign_id varchar(128) not null,              -- 广告活动标识
    impressions bigint not null default 0,          -- 当日累计曝光量 (Impressions)
    clicks bigint not null default 0,               -- 当日累计点击量 (Clicks)
    conversions bigint not null default 0,          -- 当日累计转化量 (Conversions)
    spend numeric(19,6) not null default 0,         -- 当日广告主消耗金额 (USD)
    revenue numeric(19,6) not null default 0,       -- 当日媒体/平台营收金额 (USD)
    primary key (tenant_id, report_date, campaign_id)
);

comment on table report_daily is '广告效果与资金流水多维聚合日报表';
comment on column report_daily.tenant_id is '所属租户';
comment on column report_daily.report_date is '统计业务发生日期';
comment on column report_daily.campaign_id is '广告活动 ID';
comment on column report_daily.impressions is '曝光展示次数 PV';
comment on column report_daily.clicks is '点击次数 Click';
comment on column report_daily.conversions is '转化动作次数 Conversion';
comment on column report_daily.spend is '广告主消耗总金额';
comment on column report_daily.revenue is '平台营收总金额';

create index if not exists ix_report_daily_range on report_daily(tenant_id, report_date desc);

-- 2. 小时级效果监控表 (用于大促实时大盘与预算消耗速率 Pacing 监控)
create table if not exists report_hourly (
    tenant_id varchar(64) not null default 'public',
    report_hour timestamptz not null,               -- 小时窗口时间点 (如 2026-09-02 18:00:00+00)
    campaign_id varchar(128) not null,
    impressions bigint not null default 0,
    clicks bigint not null default 0,
    conversions bigint not null default 0,
    spend numeric(19,6) not null default 0,
    revenue numeric(19,6) not null default 0,
    primary key (tenant_id, report_hour, campaign_id)
);

comment on table report_hourly is '小时级细粒度效果与预算消耗速率监控表';
comment on column report_hourly.tenant_id is '租户标识';
comment on column report_hourly.report_hour is '统计小时时间点';
comment on column report_hourly.campaign_id is '广告活动 ID';
comment on column report_hourly.impressions is '小时曝光量';
comment on column report_hourly.clicks is '小时点击量';
comment on column report_hourly.conversions is '小时转化量';
comment on column report_hourly.spend is '小时消耗';
comment on column report_hourly.revenue is '小时营收';

create index if not exists ix_report_hourly_time on report_hourly(tenant_id, report_hour desc);
