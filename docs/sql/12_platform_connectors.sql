-- ===================================================================
-- 模块名称：platform-google-ads & platform-google-gam (三方平台连接器)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. partner_sync_job    : 外部媒体与广告主物料/报表异步数据同步作业记录表
--   2. partner_token_store : Google OAuth 2.0 长期 Refresh Token 安全凭据存储表
-- ===================================================================

-- 1. 三方物料与数据同步作业记录表
create table if not exists partner_sync_job (
    id varchar(64) primary key,                     -- 作业流水 ID (如 "job_sync_gam_1001")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    partner_id varchar(64) not null,                -- 关联 partner_connection 表的主键
    provider varchar(32) not null,                  -- 渠道标识 (GAM, GOOGLE_ADS, THE_TRADE_DESK)
    status varchar(32) not null default 'PENDING',  -- 状态 (PENDING 队列中, RUNNING 执行中, SUCCESS 成功, FAILED 失败)
    imported_count int not null default 0,          -- 成功导入/同步物料数量
    rejected_count int not null default 0,          -- 格式不符被拒物料数量
    error_message text,                             -- 异常诊断错误详情
    started_at timestamptz,                         -- 作业开始时间
    finished_at timestamptz,                        -- 作业完成时间
    created_at timestamptz not null default now()   -- 创建时间
);

comment on table partner_sync_job is '外部广告平台物料与订单异步同步作业流水表';
comment on column partner_sync_job.id is '同步作业全局唯一 ID';
comment on column partner_sync_job.tenant_id is '所属租户';
comment on column partner_sync_job.partner_id is '合作方连接 ID';
comment on column partner_sync_job.provider is '服务商类型';
comment on column partner_sync_job.status is '作业执行状态';
comment on column partner_sync_job.imported_count is '成功处理条数';
comment on column partner_sync_job.rejected_count is '校验失败拒绝条数';
comment on column partner_sync_job.error_message is '失败错误日志信息';
comment on column partner_sync_job.started_at is '执行启动时间';
comment on column partner_sync_job.finished_at is '执行结束时间';

create index if not exists ix_sync_job_partner on partner_sync_job(tenant_id, partner_id, created_at desc);

-- 2. OAuth 2.0 长期访问凭据机密表
create table if not exists partner_token_store (
    id varchar(64) primary key,                     -- 凭据主键 (如 "token_gam_tenant1")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    provider varchar(32) not null,                  -- 渠道提供商 (如 "GOOGLE")
    access_token text not null,                     -- 短期访问 Access Token (密文或 Bearer)
    refresh_token text not null,                    -- 长期刷新凭据 Refresh Token (密文存储)
    token_type varchar(32) not null default 'Bearer',-- Token 类别
    expires_at timestamptz not null,                -- Access Token 到期时间戳
    updated_at timestamptz not null default now(),  -- 凭据刷新时间
    constraint uk_token_tenant_provider unique (tenant_id, provider)
);

comment on table partner_token_store is '外部平台 OAuth 2.0 刷新凭据与授权令牌安全机密表';
comment on column partner_token_store.id is '凭据主键';
comment on column partner_token_store.tenant_id is '所属租户';
comment on column partner_token_store.provider is '平台提供商标识';
comment on column partner_token_store.access_token is '短期 Access Token';
comment on column partner_token_store.refresh_token is '长期 Refresh Token';
comment on column partner_token_store.token_type is '令牌类型';
comment on column partner_token_store.expires_at is '令牌失效截止时间';
comment on column partner_token_store.updated_at is '最后更新时间';
