-- ===================================================================
-- 模块名称：platform-ssp (供给方媒体与广告位管理模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. ad_slot : 媒体广告位规格与底价主表
-- ===================================================================

create table if not exists ad_slot (
    id varchar(64) primary key,                     -- 广告位全局唯一标识 (如 "slot_728x90_01")
    tenant_id varchar(64) not null default 'public',-- 媒体所属租户
    name varchar(200) not null,                     -- 广告位名称描述
    width int not null check (width > 0),           -- 版位宽度像素 (必须 > 0)
    height int not null check (height > 0),         -- 版位高度像素 (必须 > 0)
    floor_price numeric(19,6) not null default 0 check (floor_price >= 0), -- 竞价保底价格 (USD，非负)
    secure boolean not null default true,           -- 是否强制要求物料支持 HTTPS
    active boolean not null default true,           -- 广告位是否处于开启接单状态
    created_at timestamptz not null default now()   -- 创建时间戳
);

comment on table ad_slot is '媒体发布商广告位配置主表';
comment on column ad_slot.id is '广告位唯一标识';
comment on column ad_slot.tenant_id is '所属租户空间';
comment on column ad_slot.name is '广告位名称';
comment on column ad_slot.width is '版位像素宽';
comment on column ad_slot.height is '版位像素高';
comment on column ad_slot.floor_price is '最低竞价底价 (Floor Price)';
comment on column ad_slot.secure is '是否强制要求安全链路 (HTTPS)';
comment on column ad_slot.active is '是否开启状态';
comment on column ad_slot.created_at is '创建时间戳';

create index if not exists ix_ad_slot_tenant_active on ad_slot(tenant_id, active);
create index if not exists ix_ad_slot_dimensions on ad_slot(tenant_id, width, height) where active = true;
