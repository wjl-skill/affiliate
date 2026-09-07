-- =====================================================================================
-- 广告联盟程序化聚合交易平台 (Affiliate Platform) - 全模块全量 DDL 初始化脚本
-- 适用数据库：PostgreSQL 14+
-- 包含模块：
--   01. platform-tenant      : 租户组织主表 (tenant), 外部连接表 (partner_connection)
--   02. platform-creative    : 广告素材物料表 (creative)
--   03. platform-ssp         : 媒体广告位规格表 (ad_slot)
--   04. platform-dsp         : 广告活动与定向表 (campaign)
--   05. platform-adx         : 实时竞价拍卖成交事实表 (auction)
--   06. platform-budget      : 活动预算配置表 (campaign_budget), 竞价资金预占表 (budget_reservation)
--   07. platform-event       : 事务发件箱表 (event_outbox), 审计日志表 (audit_event)
--   08. platform-dmp         : 匿名受众分群表 (dmp_segment), 客群成员映射表 (dmp_segment_member)
--   09. platform-cdp         : 一方客户画像档案表 (cdp_profile), 确定性身份图谱表 (cdp_identity_graph)
--   10. platform-billing     : 资金清算账户表 (billing_account), 复式记账分录表 (billing_entry)
--   11. platform-reporting   : 聚合效果日报表 (report_daily), 小时级趋势表 (report_hourly)
--   12. platform-connectors  : 外部平台同步作业表 (partner_sync_job), OAuth 凭据表 (partner_token_store)
-- =====================================================================================

-- 启用常用扩展 (UUID 支持)
create extension if not exists "uuid-ossp";

-- =====================================================================================
-- 01. platform-tenant: 多租户管理
-- =====================================================================================
create table if not exists tenant (
    id varchar(64) primary key,
    name varchar(200) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now()
);
comment on table tenant is '多租户企业与组织空间主表';
create index if not exists ix_tenant_status on tenant(status);

create table if not exists partner_connection (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(200) not null,
    type varchar(32) not null,
    endpoint varchar(512) not null,
    settings jsonb not null default '{}'::jsonb,
    status varchar(32) not null default 'ACTIVE',
    updated_at timestamptz not null default now(),
    constraint fk_partner_tenant foreign key (tenant_id) references tenant(id) on delete cascade
);
comment on table partner_connection is '外部生态广告平台集成连接配置表';
create index if not exists ix_partner_tenant_status on partner_connection(tenant_id, status);

-- =====================================================================================
-- 02. platform-creative: 广告素材管理
-- =====================================================================================
create table if not exists creative (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(200) not null,
    type varchar(32) not null,
    asset_url text not null,
    landing_url text not null,
    width int not null check (width > 0),
    height int not null check (height > 0),
    categories jsonb not null default '[]'::jsonb,
    active boolean not null default true,
    created_at timestamptz not null default now()
);
comment on table creative is '广告素材物料信息主表';
create index if not exists ix_creative_tenant_active on creative(tenant_id, active, created_at desc);
create index if not exists ix_creative_dimensions on creative(tenant_id, width, height) where active = true;

-- =====================================================================================
-- 03. platform-ssp: 供给方媒体与广告位
-- =====================================================================================
create table if not exists ad_slot (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(200) not null,
    width int not null check (width > 0),
    height int not null check (height > 0),
    floor_price numeric(19,6) not null default 0 check (floor_price >= 0),
    secure boolean not null default true,
    active boolean not null default true,
    created_at timestamptz not null default now()
);
comment on table ad_slot is '媒体发布商广告位配置主表';
create index if not exists ix_ad_slot_tenant_active on ad_slot(tenant_id, active);
create index if not exists ix_ad_slot_dimensions on ad_slot(tenant_id, width, height) where active = true;

-- =====================================================================================
-- 04. platform-dsp: 需求方广告活动与定向
-- =====================================================================================
create table if not exists campaign (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    advertiser_id varchar(128) not null,
    name varchar(200) not null,
    start_date date not null,
    end_date date not null,
    daily_budget numeric(19,6) not null check (daily_budget >= 0),
    max_bid numeric(19,6) not null check (max_bid >= 0),
    target_domains jsonb not null default '[]'::jsonb,
    target_device_types jsonb not null default '[]'::jsonb,
    status varchar(32) not null default 'DRAFT',
    created_at timestamptz not null default now(),
    constraint ck_campaign_dates check (end_date >= start_date)
);
comment on table campaign is '广告主投放计划与排期活动主表';
create index if not exists ix_campaign_tenant_status on campaign(tenant_id, status);
create index if not exists ix_campaign_advertiser on campaign(tenant_id, advertiser_id);
create index if not exists ix_campaign_schedule on campaign(tenant_id, start_date, end_date) where status = 'ACTIVE';

-- =====================================================================================
-- 05. platform-adx: 实时竞价撮合与拍卖成交
-- =====================================================================================
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
comment on table auction is 'RTB 实时竞价拍卖成交事实快照表';
create index if not exists ix_auction_tenant_time on auction(tenant_id, created_at desc);
create index if not exists ix_auction_request on auction(request_id);
create index if not exists ix_auction_slot on auction(tenant_id, ad_slot_id, created_at desc);

-- =====================================================================================
-- 06. platform-budget: 预算管控与原子预占
-- =====================================================================================
create table if not exists campaign_budget (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    campaign_id varchar(64) not null,
    total_budget numeric(19,6) not null default 0,
    daily_budget numeric(19,6) not null default 0,
    balance numeric(19,6) not null default 0,
    updated_at timestamptz not null default now(),
    constraint uk_budget_tenant_campaign unique (tenant_id, campaign_id)
);
comment on table campaign_budget is '广告活动预算配置与余额主表';

create table if not exists budget_reservation (
    id uuid primary key,
    tenant_id varchar(64) not null default 'public',
    campaign_id varchar(64) not null,
    user_id varchar(128),
    amount numeric(19,6) not null check (amount >= 0),
    status varchar(32) not null default 'RESERVED',
    created_at timestamptz not null default now(),
    confirmed_at timestamptz,
    expires_at timestamptz not null
);
comment on table budget_reservation is '实时竞价资金原子预占与对账流水表';
create index if not exists ix_reservation_tenant_campaign on budget_reservation(tenant_id, campaign_id, status);
create index if not exists ix_reservation_expiry on budget_reservation(status, expires_at);

-- =====================================================================================
-- 07. platform-event: 事务发件箱与审计日志
-- =====================================================================================
create table if not exists event_outbox (
    id uuid primary key,
    tenant_id varchar(64) not null default 'public',
    event_type varchar(160) not null,
    aggregate_id varchar(128) not null,
    payload jsonb not null,
    occurred_at timestamptz not null,
    published_at timestamptz,
    attempts int not null default 0
);
comment on table event_outbox is '事务发件箱表（支撑本地事务与 Kafka 最终一致性）';
create index if not exists ix_outbox_unpublished on event_outbox(published_at, occurred_at) where published_at is null;

create table if not exists audit_event (
    id uuid primary key,
    tenant_id varchar(64) not null default 'public',
    actor_id varchar(128),
    action varchar(128) not null,
    resource_type varchar(64) not null,
    resource_id varchar(128),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
comment on table audit_event is '平台安全合规与操作审计流水表';
create index if not exists ix_audit_event_tenant_time on audit_event(tenant_id, created_at desc);

-- =====================================================================================
-- 08. platform-dmp: 数据管理平台受众资产
-- =====================================================================================
create table if not exists dmp_segment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(200) not null,
    source varchar(64) not null,
    taxonomy varchar(128),
    member_count bigint not null default 0,
    status varchar(32) not null default 'DRAFT',
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);
comment on table dmp_segment is 'DMP 匿名受众分群元数据表';
create index if not exists ix_dmp_seg_tenant on dmp_segment(tenant_id, status, expires_at);

create table if not exists dmp_segment_member (
    segment_id varchar(64) not null references dmp_segment(id) on delete cascade,
    anonymous_id varchar(128) not null,
    added_at timestamptz not null default now(),
    primary key (segment_id, anonymous_id)
);
comment on table dmp_segment_member is '受众分群匿名成员映射明细表';
create index if not exists ix_dmp_member_reverse on dmp_segment_member(anonymous_id);

-- =====================================================================================
-- 09. platform-cdp: 客户数据平台一方画像与身份图谱
-- =====================================================================================
create table if not exists cdp_profile (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    primary_id varchar(128) not null,
    status varchar(32) not null default 'ACTIVE',
    identifiers jsonb not null default '[]'::jsonb,
    attributes jsonb not null default '{}'::jsonb,
    traits jsonb not null default '[]'::jsonb,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);
comment on table cdp_profile is 'CDP 企业一方持久化客户画像档案主表';
create unique index if not exists ux_cdp_profile_tenant_primary on cdp_profile(tenant_id, primary_id);
create index if not exists ix_cdp_profile_tenant_status on cdp_profile(tenant_id, status);

create table if not exists cdp_identity_graph (
    tenant_id varchar(64) not null default 'public',
    identifier_type varchar(32) not null,
    identifier_val varchar(256) not null,
    profile_id varchar(64) not null references cdp_profile(id) on delete cascade,
    linked_at timestamptz not null default now(),
    primary key (tenant_id, identifier_type, identifier_val)
);
comment on table cdp_identity_graph is 'CDP 确定性跨端身份图谱打通索引表';
create index if not exists ix_cdp_graph_profile on cdp_identity_graph(profile_id);

-- =====================================================================================
-- 10. platform-billing: 财务账户与复式记账账本
-- =====================================================================================
create table if not exists billing_account (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    account_type varchar(32) not null,
    balance numeric(19,6) not null default 0,
    currency char(3) not null default 'USD',
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
comment on table billing_account is '财务清算资金账户主表';
create index if not exists ix_billing_account_tenant on billing_account(tenant_id, account_type);

create table if not exists billing_entry (
    id uuid primary key,
    tenant_id varchar(64) not null default 'public',
    account_id varchar(128) not null,
    auction_id varchar(128),
    entry_type varchar(32) not null,
    direction varchar(16) not null default 'DEBIT',
    amount numeric(19,6) not null check (amount >= 0),
    currency char(3) not null default 'USD',
    idempotency_key varchar(256) not null unique,
    description varchar(256),
    occurred_at timestamptz not null
);
comment on table billing_entry is '复式记账不可变会计凭证分录表';
create index if not exists ix_billing_tenant_time on billing_entry(tenant_id, occurred_at desc);
create index if not exists ix_billing_account_time on billing_entry(tenant_id, account_id, occurred_at desc);
create index if not exists ix_billing_auction on billing_entry(auction_id) where auction_id is not null;

-- =====================================================================================
-- 11. platform-reporting: 多维统计与趋势报表
-- =====================================================================================
create table if not exists report_daily (
    tenant_id varchar(64) not null default 'public',
    report_date date not null,
    campaign_id varchar(128) not null,
    impressions bigint not null default 0,
    clicks bigint not null default 0,
    conversions bigint not null default 0,
    spend numeric(19,6) not null default 0,
    revenue numeric(19,6) not null default 0,
    primary key (tenant_id, report_date, campaign_id)
);
comment on table report_daily is '广告效果与资金流水多维聚合日报表';
create index if not exists ix_report_daily_range on report_daily(tenant_id, report_date desc);

create table if not exists report_hourly (
    tenant_id varchar(64) not null default 'public',
    report_hour timestamptz not null,
    campaign_id varchar(128) not null,
    impressions bigint not null default 0,
    clicks bigint not null default 0,
    conversions bigint not null default 0,
    spend numeric(19,6) not null default 0,
    revenue numeric(19,6) not null default 0,
    primary key (tenant_id, report_hour, campaign_id)
);
comment on table report_hourly is '小时级细粒度效果与预算消耗速率监控表';
create index if not exists ix_report_hourly_time on report_hourly(tenant_id, report_hour desc);

-- =====================================================================================
-- 12. platform-connectors: 三方生态对接与凭据管理
-- =====================================================================================
create table if not exists partner_sync_job (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    partner_id varchar(64) not null,
    provider varchar(32) not null,
    status varchar(32) not null default 'PENDING',
    imported_count int not null default 0,
    rejected_count int not null default 0,
    error_message text,
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now()
);
comment on table partner_sync_job is '外部广告平台物料与订单异步同步作业流水表';
create index if not exists ix_sync_job_partner on partner_sync_job(tenant_id, partner_id, created_at desc);

create table if not exists partner_token_store (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    provider varchar(32) not null,
    access_token text not null,
    refresh_token text not null,
    token_type varchar(32) not null default 'Bearer',
    expires_at timestamptz not null,
    updated_at timestamptz not null default now(),
    constraint uk_token_tenant_provider unique (tenant_id, provider)
);
comment on table partner_token_store is '外部平台 OAuth 2.0 刷新凭据与授权令牌安全机密表';

-- =====================================================================================
-- 13. platform-affiliate: 效果营销与商业级网盟核心体系
-- =====================================================================================
create table if not exists affiliate_partner (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(128) not null,
    status varchar(32) not null default 'ACTIVE',
    tier varchar(32) not null default 'STANDARD',
    postback_url_template text,
    payment_term varchar(32) not null default 'NET_30',
    min_payout_threshold numeric(12,2) not null default 100.00,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
comment on table affiliate_partner is '联盟营销渠道客(Publisher/Affiliate)主表';
create index if not exists ix_affiliate_partner_tenant on affiliate_partner(tenant_id, status);

create table if not exists affiliate_offer (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    advertiser_id varchar(64) not null,
    title varchar(256) not null,
    landing_page_url text not null,
    payout_type varchar(32) not null default 'CPA',
    default_payout numeric(12,4) not null default 0.0000,
    default_revenue numeric(12,4) not null default 0.0000,
    status varchar(32) not null default 'ACTIVE',
    daily_conversion_cap int not null default 0,
    daily_revenue_cap numeric(12,2),
    fallback_offer_id varchar(64),
    allowed_countries text[],
    allowed_devices int[],
    expires_at timestamptz,
    created_at timestamptz not null default now()
);
comment on table affiliate_offer is '网盟推广计划(Offer)主表';
create index if not exists ix_affiliate_offer_tenant on affiliate_offer(tenant_id, status);
create index if not exists ix_affiliate_offer_adv on affiliate_offer(tenant_id, advertiser_id);

create table if not exists affiliate_offer_tier_payout (
    id bigserial primary key,
    offer_id varchar(64) not null,
    affiliate_id varchar(64),
    target_tier varchar(32),
    custom_payout numeric(12,4) not null,
    custom_revenue numeric(12,4) not null,
    created_at timestamptz not null default now()
);
comment on table affiliate_offer_tier_payout is '渠道专属阶梯出价规则表';
create index if not exists ix_offer_tier_lookup on affiliate_offer_tier_payout(offer_id, affiliate_id, target_tier);

create table if not exists affiliate_smart_link (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(128) not null,
    category varchar(64),
    target_offer_ids text[] not null,
    routing_strategy varchar(32) not null default 'HIGHEST_EPC',
    fallback_offer_id varchar(64),
    created_at timestamptz not null default now()
);
comment on table affiliate_smart_link is '智能分流链接(SmartLink / TDS)表';

create table if not exists affiliate_click_session (
    click_id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    offer_id varchar(64) not null,
    affiliate_id varchar(64) not null,
    sub1 varchar(128),
    sub2 varchar(128),
    sub3 varchar(128),
    sub4 varchar(128),
    sub5 varchar(128),
    ip varchar(64),
    user_agent text,
    country varchar(8),
    device_type int not null default 1,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
comment on table affiliate_click_session is '点击追踪会话存根表';
create index if not exists ix_click_session_offer_aff on affiliate_click_session(offer_id, affiliate_id, created_at desc);
create index if not exists ix_click_session_expires on affiliate_click_session(expires_at);

create table if not exists affiliate_conversion (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    click_id varchar(64) not null,
    tx_id varchar(128) not null,
    offer_id varchar(64) not null,
    affiliate_id varchar(64) not null,
    payout numeric(12,4) not null default 0.0000,
    revenue numeric(12,4) not null default 0.0000,
    sale_amount numeric(12,2) default 0.00,
    ctit_seconds bigint not null default 0,
    status varchar(32) not null default 'PENDING',
    rejection_reason varchar(256),
    created_at timestamptz not null default now()
);
comment on table affiliate_conversion is 'S2S 服务端转化事实表';
create unique index if not exists uk_conversion_offer_tx on affiliate_conversion(offer_id, tx_id);
create index if not exists ix_conversion_aff_status on affiliate_conversion(tenant_id, affiliate_id, status, created_at desc);

create table if not exists affiliate_invoice (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    affiliate_id varchar(64) not null,
    amount numeric(12,2) not null check (amount > 0),
    conversion_count int not null,
    payment_term varchar(32) not null,
    status varchar(32) not null default 'GENERATED',
    created_at timestamptz not null default now()
);
comment on table affiliate_invoice is '渠道营销结算发票账单表';
create index if not exists ix_affiliate_invoice_lookup on affiliate_invoice(tenant_id, affiliate_id, status);

create table if not exists affiliate_sub_id_stats (
    tenant_id varchar(64) not null default 'public',
    affiliate_id varchar(64) not null,
    sub1 varchar(128) not null default 'default',
    clicks bigint not null default 0,
    conversions bigint not null default 0,
    total_payout numeric(14,4) not null default 0,
    total_revenue numeric(14,4) not null default 0,
    epc numeric(10,4) not null default 0,
    cr_percent numeric(8,2) not null default 0,
    updated_at timestamptz not null default now(),
    primary key (tenant_id, affiliate_id, sub1)
);
comment on table affiliate_sub_id_stats is 'Sub-ID 维度流式多维统计表';

-- =====================================================================================
-- 14. platform-system: 企业级系统管理中心 (用户、角色、权限、菜单、S3、域名池)
-- =====================================================================================
create table if not exists sys_user_account (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    username varchar(64) not null,
    display_name varchar(128) not null,
    email varchar(128) not null,
    phone varchar(32),
    avatar text,
    password_hash text,
    status varchar(32) not null default 'ACTIVE',
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_sys_user_username unique (tenant_id, username)
);
comment on table sys_user_account is '管理平台系统用户账号主表';
create index if not exists ix_sys_user_tenant on sys_user_account(tenant_id, status);

create table if not exists sys_role_definition (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    role_code varchar(64) not null,
    role_name varchar(128) not null,
    description text,
    data_scope varchar(32) not null default 'TENANT_ONLY',
    is_system boolean not null default false,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    constraint uk_sys_role_code unique (tenant_id, role_code)
);
comment on table sys_role_definition is '系统 RBAC 角色与多维数据权限范围定义表';

create table if not exists sys_user_role (
    user_id varchar(64) not null,
    role_id varchar(64) not null,
    created_at timestamptz not null default now(),
    primary key (user_id, role_id)
);
comment on table sys_user_role is '系统用户与角色映射多对多关联表';

create table if not exists sys_role_permission (
    role_id varchar(64) not null,
    permission_code varchar(128) not null,
    created_at timestamptz not null default now(),
    primary key (role_id, permission_code)
);
comment on table sys_role_permission is '系统角色细粒度功能操作权限绑定表';

create table if not exists sys_menu (
    id varchar(64) primary key,
    parent_id varchar(64) not null default '0',
    title varchar(128) not null,
    icon varchar(64),
    path varchar(256) not null,
    component varchar(256),
    permission_code varchar(128),
    sort_order int not null default 0,
    visible boolean not null default true,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now()
);
comment on table sys_menu is '前端侧边栏动态路由树与菜单配置表';
create index if not exists ix_sys_menu_parent on sys_menu(parent_id, sort_order);

create table if not exists sys_role_menu (
    role_id varchar(64) not null,
    menu_id varchar(64) not null,
    created_at timestamptz not null default now(),
    primary key (role_id, menu_id)
);
comment on table sys_role_menu is '系统角色与可访问菜单树授权绑定表';

create table if not exists sys_s3_storage_config (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(128) not null,
    provider varchar(32) not null default 'AWS_S3',
    region varchar(64) not null default 'us-east-1',
    endpoint text not null,
    bucket_name varchar(128) not null,
    access_key_id varchar(128) not null,
    secret_access_key text not null,
    public_cdn_url text,
    path_prefix varchar(128) default 'creatives/',
    is_default boolean not null default false,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now()
);
comment on table sys_s3_storage_config is '多云 S3 对象存储凭据与 CDN 加速配置表';
create index if not exists ix_sys_s3_tenant on sys_s3_storage_config(tenant_id, is_default);

create table if not exists sys_tracking_domain (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    domain varchar(256) not null,
    domain_type varchar(32) not null default 'TRACKING',
    cname_target varchar(256) not null default 'lb-global.affnetwork.com',
    dns_status varchar(32) not null default 'PENDING_CNAME',
    ssl_status varchar(32) not null default 'AUTO_SSL_ACTIVE',
    assigned_affiliate_id varchar(64),
    is_default boolean not null default false,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    constraint uk_sys_domain unique (domain)
);
comment on table sys_tracking_domain is '推广点击跟踪与防红分流域名池表';
create index if not exists ix_sys_domain_lookup on sys_tracking_domain(tenant_id, domain_type, status);

-- ====================================================================
-- Section 15: 商业级全业务模块扩展与数据持久化体系
-- ====================================================================

-- 1. 推广计划多事件漏斗转化目标表 (Offer Goals)
CREATE TABLE IF NOT EXISTS affiliate_offer_goal (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    offer_id VARCHAR(64) NOT NULL,
    goal_name VARCHAR(128) NOT NULL,
    goal_type VARCHAR(32) NOT NULL,
    payout_type VARCHAR(32) NOT NULL DEFAULT 'FLAT',
    payout NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    revenue NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_offer_goal_offer ON affiliate_offer_goal(tenant_id, offer_id, status);

-- 2. 4D 反作弊动态风控黑名单表 (Anti-Fraud Blacklist)
CREATE TABLE IF NOT EXISTS affiliate_antifraud_blacklist (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_value VARCHAR(128) NOT NULL,
    reason VARCHAR(255),
    operator VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_antifraud_bl ON affiliate_antifraud_blacklist(tenant_id, target_type, target_value, status);

-- 3. 4D 反作弊作弊拦截与风控审计流水表 (Anti-Fraud Audit Log)
CREATE TABLE IF NOT EXISTS affiliate_antifraud_audit_log (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(128),
    click_id VARCHAR(128),
    affiliate_id VARCHAR(64),
    ip VARCHAR(64),
    ctit_seconds NUMERIC(10,2),
    risk_score INTEGER NOT NULL DEFAULT 0,
    primary_reason VARCHAR(128),
    action VARCHAR(32) NOT NULL,
    details_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_antifraud_audit ON affiliate_antifraud_audit_log(tenant_id, created_at DESC);

-- 4. 出海批量打款结算批次表 (Mass Payout Batch Header)
CREATE TABLE IF NOT EXISTS billing_payout_batch (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    batch_number VARCHAR(64) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    item_count INTEGER NOT NULL DEFAULT 0,
    total_gross_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    total_tax_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    total_net_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    disbursed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payout_batch ON billing_payout_batch(tenant_id, batch_number);

-- 5. 出海批量打款明细清单从表 (Mass Payout Item Line)
CREATE TABLE IF NOT EXISTS billing_payout_item (
    id VARCHAR(64) PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    affiliate_id VARCHAR(64) NOT NULL,
    beneficiary_name VARCHAR(128),
    tax_id VARCHAR(64),
    tax_rate NUMERIC(6,4) NOT NULL DEFAULT 0.0000,
    gross_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    tax_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(16) NOT NULL DEFAULT 'USD',
    fx_rate NUMERIC(12,6) NOT NULL DEFAULT 1.000000,
    target_amount NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    method VARCHAR(32) NOT NULL,
    account VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payout_item ON billing_payout_item(batch_id, affiliate_id);

-- 6. 外汇实时点差汇率表 (Currency FX Rates)
CREATE TABLE IF NOT EXISTS billing_currency_fx_rate (
    id VARCHAR(64) PRIMARY KEY,
    source_currency VARCHAR(16) NOT NULL DEFAULT 'USD',
    target_currency VARCHAR(16) NOT NULL,
    base_rate NUMERIC(12,6) NOT NULL,
    spread_rate NUMERIC(6,4) NOT NULL DEFAULT 0.0150,
    effective_rate NUMERIC(12,6) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_fx_pair ON billing_currency_fx_rate(source_currency, target_currency);

-- 7. CDP 客户实时行为聚合画像快照表 (CDP User Traits)
CREATE TABLE IF NOT EXISTS cdp_user_trait_state (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    primary_id VARCHAR(128) NOT NULL,
    total_spend NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    purchase_count INTEGER NOT NULL DEFAULT 0,
    click_count INTEGER NOT NULL DEFAULT 0,
    pageview_count INTEGER NOT NULL DEFAULT 0,
    events_7d_count INTEGER NOT NULL DEFAULT 0,
    preferred_category VARCHAR(64),
    preferred_device VARCHAR(32),
    traits_json TEXT,
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cdp_user ON cdp_user_trait_state(tenant_id, primary_id);

-- 8. Cohort 留存分析用户获客支出表 (Cohort Acquisition)
CREATE TABLE IF NOT EXISTS report_cohort_acquisition (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    cohort_date DATE NOT NULL,
    cost NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cohort_acq ON report_cohort_acquisition(tenant_id, cohort_date);

-- 9. Cohort 留存分析用户回访创收流水表 (Cohort Activity)
CREATE TABLE IF NOT EXISTS report_cohort_activity (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    activity_date DATE NOT NULL,
    revenue NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cohort_act ON report_cohort_activity(tenant_id, user_id, activity_date);



