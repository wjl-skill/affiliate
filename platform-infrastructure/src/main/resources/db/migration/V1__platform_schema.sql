create table if not exists tenant (
    id varchar(64) primary key,
    name varchar(200) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now()
);

create table if not exists audit_event (
    id uuid primary key,
    tenant_id varchar(64) not null references tenant(id),
    actor_id varchar(128),
    action varchar(128) not null,
    resource_type varchar(64) not null,
    resource_id varchar(128),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index if not exists ix_audit_event_tenant_time on audit_event(tenant_id, created_at desc);

create table if not exists event_outbox (
    id uuid primary key,
    tenant_id varchar(64) not null,
    event_type varchar(160) not null,
    aggregate_id varchar(128) not null,
    payload jsonb not null,
    occurred_at timestamptz not null,
    published_at timestamptz,
    attempts int not null default 0
);
create index if not exists ix_outbox_unpublished on event_outbox(published_at, occurred_at);

create table if not exists billing_entry (
    id uuid primary key,
    tenant_id varchar(64) not null,
    account_id varchar(128) not null,
    auction_id varchar(128),
    entry_type varchar(32) not null,
    amount numeric(19,6) not null,
    currency char(3) not null,
    idempotency_key varchar(256) not null unique,
    occurred_at timestamptz not null
);
create index if not exists ix_billing_tenant_time on billing_entry(tenant_id, occurred_at desc);

create table if not exists report_daily (
    tenant_id varchar(64) not null,
    report_date date not null,
    campaign_id varchar(128),
    impressions bigint not null default 0,
    clicks bigint not null default 0,
    conversions bigint not null default 0,
    spend numeric(19,6) not null default 0,
    revenue numeric(19,6) not null default 0,
    primary key (tenant_id, report_date, campaign_id)
);
