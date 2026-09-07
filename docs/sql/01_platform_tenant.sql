-- ===================================================================
-- 模块名称：platform-tenant (多租户管理与外部连接模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. tenant               : 租户组织主表
--   2. partner_connection   : 外部生态合作方集成连接表
-- ===================================================================

-- 1. 租户组织信息表
create table if not exists tenant (
    id varchar(64) primary key,                     -- 租户全局唯一主键 ID (如 "tenant_1001")
    name varchar(200) not null,                     -- 租户企业/组织名称
    status varchar(32) not null default 'ACTIVE',    -- 租户状态 (ACTIVE: 正常, SUSPENDED: 冻结挂起)
    created_at timestamptz not null default now()   -- 租户创建时间戳
);

comment on table tenant is '多租户企业与组织空间主表';
comment on column tenant.id is '租户唯一标识';
comment on column tenant.name is '租户组织名称';
comment on column tenant.status is '租户生命周期状态';
comment on column tenant.created_at is '创建时间';

create index if not exists ix_tenant_status on tenant(status);

-- 2. 外部生态合作方连接表 (DSP/SSP/ADX/Google 连接端点与密钥配置)
create table if not exists partner_connection (
    id varchar(64) primary key,                     -- 合作方连接唯一标识 (如 "partner_gam_01")
    tenant_id varchar(64) not null default 'public',-- 所属租户标识
    name varchar(200) not null,                     -- 连接展示名称
    type varchar(32) not null,                      -- 合作方供给角色类型 (DSP, SSP, ADX)
    endpoint varchar(512) not null,                 -- 目标服务器 API 接入端点 URL
    settings jsonb not null default '{}'::jsonb,    -- 扩展配置字典 (API Key, OAuth Token, Provider 类型等)
    status varchar(32) not null default 'ACTIVE',    -- 连接状态 (ACTIVE: 正常, PAUSED: 暂停, ERROR: 异常)
    updated_at timestamptz not null default now(),  -- 配置最后更新时间
    constraint fk_partner_tenant foreign key (tenant_id) references tenant(id) on delete cascade
);

comment on table partner_connection is '外部生态广告平台集成连接配置表';
comment on column partner_connection.id is '连接唯一主键';
comment on column partner_connection.tenant_id is '所属租户';
comment on column partner_connection.name is '连接名称';
comment on column partner_connection.type is '对接平台类型 (DSP, SSP, ADX)';
comment on column partner_connection.endpoint is '接入 API 端点 URL';
comment on column partner_connection.settings is 'JSONB 格式的鉴权参数与动态配置';
comment on column partner_connection.status is '在线同步状态';
comment on column partner_connection.updated_at is '最后更新时间';

create index if not exists ix_partner_tenant_status on partner_connection(tenant_id, status);
