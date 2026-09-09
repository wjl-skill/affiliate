-- Flyway V9: 联盟扩展业务持久化（通知、支付、归因、合规）
-- 生产环境不依赖 docs/sql 下的初始化脚本，所有服务使用的列在此集中声明。

create table if not exists affiliate_payment_method (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    type varchar(50) not null,
    credentials text not null,
    currency varchar(10) not null,
    is_primary boolean not null default false,
    status varchar(30) not null,
    last_used_at timestamptz,
    created_at timestamptz not null default now(),
    verified_at timestamptz
);
create index if not exists ix_affiliate_payment_method_affiliate on affiliate_payment_method(affiliate_id, created_at desc);
create index if not exists ix_affiliate_payment_method_status on affiliate_payment_method(status);

create table if not exists affiliate_payment_transaction (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    invoice_id varchar(64),
    payment_method_id varchar(64) not null,
    amount numeric(19,6) not null,
    currency varchar(10) not null,
    fee numeric(19,6) not null default 0,
    net_amount numeric(19,6) not null,
    payout_currency varchar(10) not null,
    exchange_rate numeric(19,8) not null default 1,
    converted_amount numeric(19,6) not null,
    status varchar(20) not null,
    external_payment_id varchar(255),
    error_message text,
    retry_count integer not null default 0,
    created_at timestamptz not null default now(),
    processed_at timestamptz,
    failed_at timestamptz
);
create index if not exists ix_affiliate_payment_tx_affiliate on affiliate_payment_transaction(affiliate_id, created_at desc);
create index if not exists ix_affiliate_payment_tx_status on affiliate_payment_transaction(status, created_at);
create unique index if not exists ux_affiliate_payment_tx_external on affiliate_payment_transaction(external_payment_id) where external_payment_id is not null;

create table if not exists affiliate_notification (
    id varchar(64) primary key,
    recipient_id varchar(64) not null,
    type varchar(50) not null,
    title varchar(255) not null,
    message text not null,
    priority varchar(20) not null,
    metadata text,
    is_read boolean not null default false,
    read_at timestamptz,
    created_at timestamptz not null default now()
);
create index if not exists ix_affiliate_notification_recipient on affiliate_notification(recipient_id, is_read, created_at desc);

create table if not exists affiliate_notification_preference (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    notification_type varchar(50) not null,
    enable_email boolean not null default true,
    enable_sms boolean not null default false,
    enable_webhook boolean not null default false,
    enable_in_app boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz
);
create unique index if not exists ux_affiliate_notification_preference on affiliate_notification_preference(affiliate_id, notification_type);

create table if not exists affiliate_webhook_endpoint (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    url varchar(1000) not null,
    secret varchar(255) not null,
    subscribed_types text not null default '[]',
    active boolean not null default true,
    failure_count integer not null default 0,
    last_failed_at timestamptz,
    last_success_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz
);
create index if not exists ix_affiliate_webhook_affiliate on affiliate_webhook_endpoint(affiliate_id, active);

create table if not exists affiliate_touch_point (
    id varchar(64) primary key,
    user_id varchar(128) not null,
    session_id varchar(128),
    type varchar(20) not null,
    affiliate_id varchar(64) not null,
    offer_id varchar(64) not null,
    click_id varchar(128),
    source varchar(100),
    medium varchar(100),
    campaign varchar(255),
    timestamp timestamptz not null default now()
);
create index if not exists ix_affiliate_touch_point_user on affiliate_touch_point(user_id, timestamp);
create index if not exists ix_affiliate_touch_point_affiliate on affiliate_touch_point(affiliate_id, timestamp);

create table if not exists affiliate_attribution_result (
    id varchar(64) primary key,
    user_id varchar(128) not null,
    conversion_id varchar(64) not null,
    conversion_value numeric(19,6) not null,
    conversion_time timestamptz not null,
    attribution_model varchar(50) not null,
    touch_point_count integer not null,
    credits text not null default '[]',
    calculated_at timestamptz not null default now()
);
create unique index if not exists ux_affiliate_attribution_conversion on affiliate_attribution_result(conversion_id);
create index if not exists ix_affiliate_attribution_user_time on affiliate_attribution_result(user_id, conversion_time desc);

create table if not exists affiliate_terms_acceptance (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    version varchar(20) not null,
    ip_address varchar(45) not null,
    user_agent varchar(500),
    accepted_at timestamptz not null default now()
);
create index if not exists ix_affiliate_terms_affiliate on affiliate_terms_acceptance(affiliate_id, version, accepted_at desc);

create table if not exists affiliate_tax_document (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    form_type varchar(50) not null,
    document_url varchar(1000) not null,
    tax_id varchar(100),
    legal_name varchar(255),
    country varchar(10),
    status varchar(20) not null,
    reviewer_note text,
    uploaded_at timestamptz not null default now(),
    reviewed_at timestamptz,
    expires_at timestamptz
);
create index if not exists ix_affiliate_tax_document_affiliate on affiliate_tax_document(affiliate_id, status);
create index if not exists ix_affiliate_tax_document_expires on affiliate_tax_document(expires_at);

create table if not exists affiliate_kyc_verification (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null unique,
    full_name varchar(255) not null,
    date_of_birth varchar(20),
    address text,
    id_document_url varchar(1000),
    status varchar(20) not null,
    rejection_reason text,
    risk_score integer not null default 0,
    initiated_at timestamptz not null default now(),
    completed_at timestamptz
);
create index if not exists ix_affiliate_kyc_status on affiliate_kyc_verification(status, initiated_at);

create table if not exists affiliate_compliance_violation (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    type varchar(50) not null,
    description text not null,
    severity varchar(20) not null,
    evidence text,
    status varchar(50) not null,
    resolution text,
    detected_at timestamptz not null default now(),
    resolved_at timestamptz
);
create index if not exists ix_affiliate_violation_affiliate_status on affiliate_compliance_violation(affiliate_id, status, detected_at desc);

create table if not exists affiliate_product (
    sku varchar(128) primary key,
    offer_id varchar(64) not null,
    name varchar(500) not null,
    description text,
    price varchar(20) not null,
    currency varchar(10) not null,
    image_url varchar(1000),
    product_url varchar(1000),
    brand varchar(200),
    category_id varchar(64),
    availability varchar(20) not null,
    stock_quantity integer not null default 0,
    attributes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists ix_affiliate_product_offer on affiliate_product(offer_id, name);
create index if not exists ix_affiliate_product_category on affiliate_product(category_id);

create table if not exists affiliate_ip_geolocation_cache (
    id varchar(64) primary key,
    ip_address varchar(45) not null unique,
    country_code varchar(10),
    country_name varchar(100),
    city varchar(100),
    region varchar(100),
    latitude double precision,
    longitude double precision,
    timezone varchar(50),
    isp varchar(255),
    asn varchar(50),
    is_vpn boolean default false,
    is_proxy boolean default false,
    is_tor boolean default false,
    is_datacenter boolean default false,
    risk_score integer default 0,
    risk_level varchar(20),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
alter table affiliate_ip_geolocation_cache add column if not exists updated_at timestamptz;
create index if not exists ix_affiliate_ip_geo_country on affiliate_ip_geolocation_cache(country_code);
create index if not exists ix_affiliate_ip_geo_risk on affiliate_ip_geolocation_cache(risk_score);
create index if not exists ix_affiliate_ip_geo_updated on affiliate_ip_geolocation_cache(updated_at);

create table if not exists affiliate_performance_report_cache (
    id varchar(64) primary key,
    report_hash varchar(64) not null unique,
    report_type varchar(30) not null,
    affiliate_id varchar(64),
    offer_id varchar(64),
    start_date date not null,
    end_date date not null,
    group_by_dimension varchar(30),
    time_granularity varchar(20),
    report_data text not null,
    row_count integer not null default 0,
    total_clicks bigint,
    total_conversions bigint,
    total_revenue numeric(19,6),
    total_payout numeric(19,6),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
create index if not exists ix_affiliate_performance_report_expiry on affiliate_performance_report_cache(expires_at);

create table if not exists affiliate_referral_relationship (
    id varchar(64) primary key,
    referee_id varchar(64) not null unique,
    referrer_id varchar(64) not null,
    referral_code varchar(100) not null,
    tier integer not null,
    status varchar(20) not null,
    total_commission_earned numeric(19,6) not null default 0,
    total_conversions bigint not null default 0,
    created_at timestamptz not null default now(),
    last_conversion_at timestamptz
);
create index if not exists ix_affiliate_referral_referrer on affiliate_referral_relationship(referrer_id, status, created_at desc);
create unique index if not exists ux_affiliate_referral_code on affiliate_referral_relationship(referral_code);

create table if not exists affiliate_referral_commission (
    id varchar(64) primary key,
    referrer_id varchar(64) not null,
    referee_id varchar(64) not null,
    conversion_id varchar(64) not null,
    tier integer not null,
    commission numeric(19,6) not null,
    base_amount numeric(19,6) not null,
    status varchar(20) not null,
    created_at timestamptz not null default now(),
    processed_at timestamptz
);
create index if not exists ix_affiliate_referral_commission_referrer on affiliate_referral_commission(referrer_id, status, created_at desc);
create index if not exists ix_affiliate_referral_commission_conversion on affiliate_referral_commission(conversion_id);

create table if not exists affiliate_offer_application (
    id varchar(64) primary key,
    offer_id varchar(64) not null,
    affiliate_id varchar(64) not null,
    promotion_plan text,
    traffic_sources text,
    status varchar(20) not null,
    reviewer_id varchar(64),
    review_note text,
    created_at timestamptz not null default now(),
    reviewed_at timestamptz
);
create unique index if not exists ux_affiliate_offer_application_offer_affiliate on affiliate_offer_application(offer_id, affiliate_id);
create index if not exists ix_affiliate_offer_application_status on affiliate_offer_application(status, created_at);

create table if not exists affiliate_creative (
    id varchar(64) primary key,
    offer_id varchar(64) not null,
    type varchar(50) not null,
    name varchar(255) not null,
    description text,
    assets text not null,
    languages text,
    metadata text,
    status varchar(30) not null,
    reviewer_note text,
    clicks bigint not null default 0,
    conversions bigint not null default 0,
    created_at timestamptz not null default now(),
    reviewed_at timestamptz
);
create index if not exists ix_affiliate_creative_offer on affiliate_creative(offer_id);
create index if not exists ix_affiliate_creative_type on affiliate_creative(type);
create index if not exists ix_affiliate_creative_status on affiliate_creative(status);

-- 兼容 docs/sql/14_affiliate_extended_tables.sql 创建的旧表结构。
-- 旧表可能没有实体所需的主键、审计时间和风控字段；仅在表已存在时补列。
do $$
begin
    if to_regclass(format('%I.%I', current_schema(), 'affiliate_notification')) is not null then
        alter table affiliate_notification add column if not exists read_at timestamptz;
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_notification_preference')) is not null then
        alter table affiliate_notification_preference add column if not exists id varchar(64);
        alter table affiliate_notification_preference add column if not exists created_at timestamptz;
        alter table affiliate_notification_preference add column if not exists updated_at timestamptz;
        update affiliate_notification_preference
           set id = md5(affiliate_id || ':' || notification_type)
         where id is null;
        update affiliate_notification_preference
           set created_at = now()
         where created_at is null;
        alter table affiliate_notification_preference alter column id set not null;
        alter table affiliate_notification_preference alter column created_at set default now();
        alter table affiliate_notification_preference alter column created_at set not null;
        create unique index if not exists ux_affiliate_notification_preference_id
            on affiliate_notification_preference(id);
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_webhook_endpoint')) is not null then
        alter table affiliate_webhook_endpoint add column if not exists last_success_at timestamptz;
        alter table affiliate_webhook_endpoint add column if not exists updated_at timestamptz;
        alter table affiliate_webhook_endpoint alter column subscribed_types type text using subscribed_types::text;
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_ip_geolocation_cache')) is not null then
        alter table affiliate_ip_geolocation_cache add column if not exists id varchar(64);
        alter table affiliate_ip_geolocation_cache add column if not exists is_proxy boolean default false;
        alter table affiliate_ip_geolocation_cache add column if not exists risk_level varchar(20);
        alter table affiliate_ip_geolocation_cache add column if not exists created_at timestamptz;
        alter table affiliate_ip_geolocation_cache add column if not exists updated_at timestamptz;
        update affiliate_ip_geolocation_cache
           set id = md5(ip_address)
         where id is null;
        if exists (
            select 1 from information_schema.columns
             where table_schema = current_schema()
               and table_name = 'affiliate_ip_geolocation_cache'
               and column_name = 'cached_at'
        ) then
            execute 'update affiliate_ip_geolocation_cache
                        set created_at = coalesce(created_at, cached_at, now()),
                            updated_at = coalesce(updated_at, cached_at, now())
                      where created_at is null or updated_at is null';
        else
            update affiliate_ip_geolocation_cache
               set created_at = coalesce(created_at, now()),
                   updated_at = coalesce(updated_at, now())
             where created_at is null or updated_at is null;
        end if;
        alter table affiliate_ip_geolocation_cache alter column id set not null;
        alter table affiliate_ip_geolocation_cache alter column created_at set default now();
        alter table affiliate_ip_geolocation_cache alter column updated_at set default now();
        alter table affiliate_ip_geolocation_cache alter column created_at set not null;
        alter table affiliate_ip_geolocation_cache alter column updated_at set not null;
        create unique index if not exists ux_affiliate_ip_geolocation_cache_id
            on affiliate_ip_geolocation_cache(id);
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_creative')) is not null then
        -- 历史初始化脚本用 JSONB，新实体以 String 读取，统一为文本列。
        alter table affiliate_creative alter column assets type text using assets::text;
        alter table affiliate_creative alter column languages type text using languages::text;
        alter table affiliate_creative alter column metadata type text using metadata::text;
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_payment_method')) is not null then
        alter table affiliate_payment_method alter column credentials type text using credentials::text;
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_product')) is not null then
        alter table affiliate_product alter column attributes type text using attributes::text;
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_attribution_result')) is not null then
        alter table affiliate_attribution_result alter column credits type text using credits::text;
    end if;

    if to_regclass(format('%I.%I', current_schema(), 'affiliate_notification')) is not null then
        alter table affiliate_notification alter column metadata type text using metadata::text;
    end if;
end $$;

-- 预算账户在早期 V2 只声明了 reservation 表，补齐 upsert 所需唯一约束与账户主表。
create table if not exists campaign_budget (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    campaign_id varchar(64) not null,
    total_budget numeric(19,6) not null default 0,
    daily_budget numeric(19,6) not null default 0,
    balance numeric(19,6) not null default 0,
    updated_at timestamptz not null default now()
);
create unique index if not exists ux_campaign_budget_tenant_campaign on campaign_budget(tenant_id, campaign_id);

create table if not exists wallet_account (
    account_id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    cash_balance numeric(19,6) not null default 0,
    credit_limit numeric(19,6) not null default 0,
    frozen_amount numeric(19,6) not null default 0,
    currency varchar(8) not null default 'USD',
    updated_at timestamptz not null default now()
);
create index if not exists ix_wallet_account_tenant on wallet_account(tenant_id);

create table if not exists api_key (
    key_id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    secret varchar(128) not null,
    active boolean not null default true,
    created_at timestamptz not null default now()
);
create index if not exists ix_api_key_tenant_active on api_key(tenant_id, active);
