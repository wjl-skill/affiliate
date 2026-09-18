-- ===================================================================
-- V12: 新增宏参数字典与平台宏映射两张全局配置表
-- affiliate_macro_param             : 本平台标准宏占位符字典
-- affiliate_platform_macro_mapping  : 第三方广告/流量平台宏写法对照表
-- ===================================================================

create table if not exists affiliate_macro_param (
    id varchar(64) primary key,                      -- 宏字典 ID (mcp_ 前缀)
    macro_key varchar(64) not null,                  -- 标准宏键 (如 click_id、sub1)
    display_name varchar(128),                       -- 宏中文名称
    description varchar(512),                        -- 宏用途说明
    sample_value varchar(256),                       -- 示例取值 (链接渲染预览用)
    category varchar(32) not null default 'ATTRIBUTION', -- 分类 (ATTRIBUTION/SUB_TRACKING/TRANSACTION/ENVIRONMENT)
    status varchar(32) not null default 'ACTIVE',    -- 状态 (ACTIVE 启用, DISABLED 停用)
    created_at timestamptz not null default now(),   -- 创建时间
    constraint uk_macro_param_key unique (macro_key)
);

comment on table affiliate_macro_param is '标准追踪宏参数字典表 (平台级全局配置)';

create table if not exists affiliate_platform_macro_mapping (
    id varchar(64) primary key,                      -- 映射记录 ID (pmm_ 前缀)
    platform_code varchar(64) not null,              -- 平台代码 (大写，如 AWIN、CJ、TIKTOK)
    platform_name varchar(128),                      -- 平台展示名称
    macro_key varchar(64) not null,                  -- 对应的本平台标准宏键
    platform_macro_token varchar(128) not null,      -- 该平台宏原生写法 (如 {clickid}、[ssn]、__CLICKID__)
    remark varchar(512),                             -- 备注说明
    status varchar(32) not null default 'ACTIVE',    -- 状态 (ACTIVE 启用, DISABLED 停用)
    created_at timestamptz not null default now(),   -- 创建时间
    updated_at timestamptz not null default now(),   -- 最近更新时间
    constraint uk_platform_macro unique (platform_code, macro_key)
);

comment on table affiliate_platform_macro_mapping is '第三方广告平台宏参数映射对照表';

create index if not exists ix_platform_macro_mapping_code on affiliate_platform_macro_mapping(platform_code, status);
