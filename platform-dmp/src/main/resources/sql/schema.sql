-- ===================================================================
-- 模块名称：platform-dmp (数据管理平台与受众分群模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. dmp_segment        : 受众客群元数据表
--   2. dmp_segment_member : 匿名客群成员物理关系表 (Cookie / Device ID)
-- ===================================================================

-- 1. 受众分群元数据定义表
create table if not exists dmp_segment (
    id varchar(64) primary key,                     -- 分群唯一主键 (如 "dmp_seg_auto_intenders")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    name varchar(200) not null,                     -- 客群业务名称
    source varchar(64) not null,                    -- 数据来源 (THIRD_PARTY, SECOND_PARTY, LOOKALIKE)
    taxonomy varchar(128),                          -- 分类标准层级 (如 "automotive/luxury")
    member_count bigint not null default 0,         -- 当前分群成员总数
    status varchar(32) not null default 'DRAFT',    -- 分群状态 (DRAFT, ACTIVE, ARCHIVED)
    expires_at timestamptz not null,                -- 客群有效截止时间戳
    created_at timestamptz not null default now()   -- 创建时间戳
);

comment on table dmp_segment is 'DMP 匿名受众分群元数据表';
comment on column dmp_segment.id is '受众分群 ID';
comment on column dmp_segment.tenant_id is '所属租户';
comment on column dmp_segment.name is '分群名称';
comment on column dmp_segment.source is '客群数据来源';
comment on column dmp_segment.taxonomy is '行业类目层级';
comment on column dmp_segment.member_count is '包含的匿名成员总数';
comment on column dmp_segment.status is '分群生命周期状态';
comment on column dmp_segment.expires_at is '失效截止时间';
comment on column dmp_segment.created_at is '创建时间戳';

create index if not exists ix_dmp_seg_tenant on dmp_segment(tenant_id, status, expires_at);

-- 2. 匿名受众成员明细关联表
create table if not exists dmp_segment_member (
    segment_id varchar(64) not null references dmp_segment(id) on delete cascade,
    anonymous_id varchar(128) not null,            -- 匿名标识 (Cookie ID / IDFA / GAID / OAID)
    added_at timestamptz not null default now(),    -- 导入时间
    primary key (segment_id, anonymous_id)
);

comment on table dmp_segment_member is '受众分群匿名成员映射明细表';
comment on column dmp_segment_member.segment_id is '关联分群 ID';
comment on column dmp_segment_member.anonymous_id is '匿名设备/Cookie 标识';
comment on column dmp_segment_member.added_at is '成员加入时间戳';

create index if not exists ix_dmp_member_reverse on dmp_segment_member(anonymous_id);
