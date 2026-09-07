-- ===================================================================
-- 模块名称：platform-cdp (客户数据平台与一方画像资产模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. cdp_profile        : 一方持久化客户画像档案主表
--   2. cdp_identity_graph : 跨渠道身份图谱反查索引表
-- ===================================================================

-- 1. 一方客户档案主表
create table if not exists cdp_profile (
    id varchar(64) primary key,                     -- CDP 统一主档案 ID (如 "cdp_profile_1001")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    primary_id varchar(128) not null,               -- 一方企业客户主标识 (如 CRM Customer No)
    status varchar(32) not null default 'ACTIVE',    -- 合规状态 (ACTIVE 正常, OPTED_OUT 隐私退出, DELETED 擦除)
    identifiers jsonb not null default '[]'::jsonb, -- 关联身份集合 (Email, 手机号, OpenID, 设备号)
    attributes jsonb not null default '{}'::jsonb,  -- 客户事实属性字典 (性别, 年龄段, VIP 等级, 注册国家)
    traits jsonb not null default '[]'::jsonb,      -- 行为标签与兴趣画像特征
    last_seen_at timestamptz not null default now(),-- 最近活跃触点时间
    created_at timestamptz not null default now()   -- 首次建档时间
);

comment on table cdp_profile is 'CDP 企业一方持久化客户画像档案主表';
comment on column cdp_profile.id is 'CDP 统一全局档案主键';
comment on column cdp_profile.tenant_id is '所属租户';
comment on column cdp_profile.primary_id is '一方客户主键 (CRM ID)';
comment on column cdp_profile.status is '合规生命周期状态';
comment on column cdp_profile.identifiers is '跨渠道身份标识 JSONB 列表';
comment on column cdp_profile.attributes is '客户属性字典 JSONB';
comment on column cdp_profile.traits is '客户特征标签 JSONB 列表';
comment on column cdp_profile.last_seen_at is '最后活跃时间';
comment on column cdp_profile.created_at is '建档时间戳';

create unique index if not exists ux_cdp_profile_tenant_primary on cdp_profile(tenant_id, primary_id);
create index if not exists ix_cdp_profile_tenant_status on cdp_profile(tenant_id, status);

-- 2. 跨渠道身份图谱多向反查映射表 (毫秒级 Deterministic ID Stitching)
create table if not exists cdp_identity_graph (
    tenant_id varchar(64) not null default 'public',
    identifier_type varchar(32) not null,           -- 标识类型 (EMAIL, PHONE, OPENID, DEVICE_ID, CRM_ID)
    identifier_val varchar(256) not null,           -- 归一化后的身份值 (如全小写邮箱或 E.164 电话号码)
    profile_id varchar(64) not null references cdp_profile(id) on delete cascade,
    linked_at timestamptz not null default now(),
    primary key (tenant_id, identifier_type, identifier_val)
);

comment on table cdp_identity_graph is 'CDP 确定性跨端身份图谱打通索引表';
comment on column cdp_identity_graph.tenant_id is '租户标识';
comment on column cdp_identity_graph.identifier_type is '标识渠道类别';
comment on column cdp_identity_graph.identifier_val is '渠道身份取值';
comment on column cdp_identity_graph.profile_id is '指向的统一客户档案 ID';
comment on column cdp_identity_graph.linked_at is '打通绑定时间';

create index if not exists ix_cdp_graph_profile on cdp_identity_graph(profile_id);
