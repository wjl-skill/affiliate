create table if not exists cdp_customer_event (
    event_id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    primary_id varchar(128) not null,
    event_type varchar(32) not null,
    event_at timestamptz not null,
    payload jsonb not null default '{}'::jsonb
);
create index if not exists ix_cdp_customer_event_lookup on cdp_customer_event(tenant_id, primary_id, event_at desc);

create table if not exists cdp_identity_graph (
    tenant_id varchar(64) not null default 'public',
    identifier_type varchar(32) not null,
    identifier_val varchar(256) not null,
    profile_id varchar(64) not null references cdp_profile(id) on delete cascade,
    linked_at timestamptz not null default now(),
    primary key (tenant_id, identifier_type, identifier_val)
);
create index if not exists ix_cdp_identity_graph_profile on cdp_identity_graph(tenant_id, profile_id);

create table if not exists billing_currency_fx_rate (
    id varchar(16) primary key,
    source_currency varchar(8) not null,
    target_currency varchar(8) not null,
    base_rate numeric(19,8) not null,
    spread_rate numeric(19,8) not null default 0,
    effective_rate numeric(19,8) not null,
    updated_at timestamptz not null default now()
);
