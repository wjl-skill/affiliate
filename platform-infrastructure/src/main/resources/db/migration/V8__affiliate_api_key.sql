create table if not exists affiliate_api_key (
    id varchar(64) primary key,
    affiliate_id varchar(64) not null,
    name varchar(255) not null,
    secret_key varchar(128) not null unique,
    scopes text not null,
    environment varchar(20) not null,
    status varchar(20) not null,
    expires_at timestamptz,
    revoked_reason varchar(500),
    usage_count bigint not null default 0,
    created_at timestamptz not null default now(),
    last_used_at timestamptz,
    revoked_at timestamptz
);
create index if not exists idx_affiliate_api_key_affiliate on affiliate_api_key(affiliate_id);
create index if not exists idx_affiliate_api_key_status on affiliate_api_key(status);
create index if not exists idx_affiliate_api_key_expires on affiliate_api_key(expires_at);
