-- ===================================================================
-- Flyway Migration V4: 商业级网盟营销核心表 (Affiliate Network Schema)
-- 包含 Offer 推广计划、渠道客、智能分流 SmartLink、点击存根、S2S 转化与账期发票
-- ===================================================================

-- 1. 联盟营销渠道客主表
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

-- 2. 推广计划(Offer)主表
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

-- 3. 渠道专属阶梯出价规则表
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

-- 4. 智能分流链接(SmartLink/TDS)表
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

-- 5. 点击追踪会话存根表
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

-- 6. S2S 服务端转化事实表
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

-- 7. 渠道周期性结算发票账单表
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

-- 8. Sub-ID 维度流式统计报表
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
