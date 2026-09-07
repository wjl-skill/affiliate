-- ===================================================================
-- 模块名称：platform-creative (广告素材管理模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. creative : 广告素材主表（支持 Banner/Video/Native/HTML5）
-- ===================================================================

create table if not exists creative (
    id varchar(64) primary key,                     -- 素材全局唯一主键 ID (如 "cr_1001")
    tenant_id varchar(64) not null default 'public',-- 租户隔离标识
    name varchar(200) not null,                     -- 素材物料展示名称
    type varchar(32) not null,                      -- 物料类型 (BANNER, VIDEO, NATIVE, HTML5)
    asset_url text not null,                        -- CDN 物料静态文件绝对 URL
    landing_url text not null,                      -- 点击目标跳转落地页 URL
    width int not null check (width > 0),           -- 素材像素宽度 (必须 > 0)
    height int not null check (height > 0),         -- 素材像素高度 (必须 > 0)
    categories jsonb not null default '[]'::jsonb,  -- IAB 行业分类标签数组 (如 '["IAB1","tech"]')
    active boolean not null default true,           -- 是否处于激活可投放状态
    created_at timestamptz not null default now()   -- 素材创建时间戳
);

comment on table creative is '广告素材物料信息主表';
comment on column creative.id is '素材主键 ID';
comment on column creative.tenant_id is '所属租户空间';
comment on column creative.name is '素材名称';
comment on column creative.type is '物料表现形态 (BANNER, VIDEO, NATIVE, HTML5)';
comment on column creative.asset_url is 'CDN 静态资源 URL';
comment on column creative.landing_url is '点击跳转落地页 URL';
comment on column creative.width is '像素宽度';
comment on column creative.height is '像素高度';
comment on column creative.categories is 'JSONB 格式的 IAB 类目标签集合';
comment on column creative.active is '是否处于激活投放状态';
comment on column creative.created_at is '创建时间戳';

-- 复合索引：按租户与活跃状态过滤并按创建时间倒序排
create index if not exists ix_creative_tenant_active on creative(tenant_id, active, created_at desc);

-- 空间与尺寸复合索引：便于按尺寸快速检索候选素材
create index if not exists ix_creative_dimensions on creative(tenant_id, width, height) where active = true;
