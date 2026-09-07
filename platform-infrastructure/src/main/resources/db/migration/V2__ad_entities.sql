-- Flyway Migration V2: Core Ad Entities & State Persistence

create table if not exists creative (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(200) not null,
    type varchar(32) not null,
    asset_url text not null,
    landing_url text not null,
    width int not null,
    height int not null,
    categories jsonb not null default '[]'::jsonb,
    active boolean not null default true,
    created_at timestamptz not null default now()
);
create index if not exists ix_creative_tenant_active on creative(tenant_id, active, created_at desc);

create table if not exists ad_slot (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(200) not null,
    width int not null,
    height int not null,
    floor_price numeric(19,6) not null default 0,
    secure boolean not null default true,
    active boolean not null default true,
    created_at timestamptz not null default now()
);
create index if not exists ix_ad_slot_tenant_active on ad_slot(tenant_id, active);

create table if not exists campaign (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    advertiser_id varchar(128) not null,
    name varchar(200) not null,
    start_date date not null,
    end_date date not null,
    daily_budget numeric(19,6) not null,
    max_bid numeric(19,6) not null,
    target_domains jsonb not null default '[]'::jsonb,
    target_device_types jsonb not null default '[]'::jsonb,
    status varchar(32) not null default 'DRAFT',
    created_at timestamptz not null default now()
);
create index if not exists ix_campaign_tenant_status on campaign(tenant_id, status);

create table if not exists budget_reservation (
    id uuid primary key,
    tenant_id varchar(64) not null,
    campaign_id varchar(64) not null,
    user_id varchar(128),
    amount numeric(19,6) not null,
    status varchar(32) not null default 'RESERVED',
    created_at timestamptz not null default now(),
    confirmed_at timestamptz,
    expires_at timestamptz not null
);
create index if not exists ix_reservation_tenant_campaign on budget_reservation(tenant_id, campaign_id, status);
create index if not exists ix_reservation_expiry on budget_reservation(status, expires_at);

create table if not exists dmp_segment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(200) not null,
    source varchar(64) not null,
    taxonomy varchar(128),
    member_count bigint not null default 0,
    status varchar(32) not null default 'DRAFT',
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);
create index if not exists ix_dmp_seg_tenant on dmp_segment(tenant_id, status, expires_at);

create table if not exists cdp_profile (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    primary_id varchar(128) not null,
    status varchar(32) not null default 'ACTIVE',
    identifiers jsonb not null default '[]'::jsonb,
    attributes jsonb not null default '{}'::jsonb,
    traits jsonb not null default '[]'::jsonb,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);
create unique index if not exists ux_cdp_profile_tenant_primary on cdp_profile(tenant_id, primary_id);
create index if not exists ix_cdp_profile_tenant_status on cdp_profile(tenant_id, status);
