-- Align the cumulative Flyway schema with the fields used by persistence entities.

alter table if exists affiliate_invoice
    add column if not exists billing_cycle varchar(32);
alter table if exists affiliate_invoice
    add column if not exists paid_at timestamptz;

create table if not exists dmp_segment_member (
    segment_id varchar(64) not null references dmp_segment(id) on delete cascade,
    anonymous_id varchar(128) not null,
    added_at timestamptz not null default now(),
    primary key (segment_id, anonymous_id)
);
create index if not exists ix_dmp_member_reverse on dmp_segment_member(anonymous_id);

comment on table dmp_segment_member is '受众分群匿名成员映射明细表';
comment on column affiliate_invoice.billing_cycle is '结算周期标识';
comment on column affiliate_invoice.paid_at is '实际支付完成时间';
