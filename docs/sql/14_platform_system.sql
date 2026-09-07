-- =====================================================================================
-- 14. platform-system: 企业级系统管理中心 (用户、角色、权限、菜单、S3、域名池)
-- =====================================================================================

-- 1. 系统用户账号主表
create table if not exists sys_user_account (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    username varchar(64) not null,
    display_name varchar(128) not null,
    email varchar(128) not null,
    phone varchar(32),
    avatar text,
    password_hash text,
    status varchar(32) not null default 'ACTIVE',
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_sys_user_username unique (tenant_id, username)
);
comment on table sys_user_account is '管理平台系统用户账号主表';
create index if not exists ix_sys_user_tenant on sys_user_account(tenant_id, status);

-- 2. 角色定义主表
create table if not exists sys_role_definition (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    role_code varchar(64) not null,
    role_name varchar(128) not null,
    description text,
    data_scope varchar(32) not null default 'TENANT_ONLY',
    is_system boolean not null default false,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    constraint uk_sys_role_code unique (tenant_id, role_code)
);
comment on table sys_role_definition is '系统 RBAC 角色与多维数据权限范围定义表';

-- 3. 用户-角色多对多关联表
create table if not exists sys_user_role (
    user_id varchar(64) not null,
    role_id varchar(64) not null,
    created_at timestamptz not null default now(),
    primary key (user_id, role_id)
);
comment on table sys_user_role is '系统用户与角色映射多对多关联表';

-- 4. 角色-权限关联表
create table if not exists sys_role_permission (
    role_id varchar(64) not null,
    permission_code varchar(128) not null,
    created_at timestamptz not null default now(),
    primary key (role_id, permission_code)
);
comment on table sys_role_permission is '系统角色细粒度功能操作权限绑定表';

-- 5. 树形动态菜单路由表
create table if not exists sys_menu (
    id varchar(64) primary key,
    parent_id varchar(64) not null default '0',
    title varchar(128) not null,
    icon varchar(64),
    path varchar(256) not null,
    component varchar(256),
    permission_code varchar(128),
    sort_order int not null default 0,
    visible boolean not null default true,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now()
);
comment on table sys_menu is '前端侧边栏动态路由树与菜单配置表';
create index if not exists ix_sys_menu_parent on sys_menu(parent_id, sort_order);

-- 6. 角色-菜单授权关联表
create table if not exists sys_role_menu (
    role_id varchar(64) not null,
    menu_id varchar(64) not null,
    created_at timestamptz not null default now(),
    primary key (role_id, menu_id)
);
comment on table sys_role_menu is '系统角色与可访问菜单树授权绑定表';

-- 7. S3 对象存储配置表
create table if not exists sys_s3_storage_config (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    name varchar(128) not null,
    provider varchar(32) not null default 'AWS_S3',
    region varchar(64) not null default 'us-east-1',
    endpoint text not null,
    bucket_name varchar(128) not null,
    access_key_id varchar(128) not null,
    secret_access_key text not null,
    public_cdn_url text,
    path_prefix varchar(128) default 'creatives/',
    is_default boolean not null default false,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now()
);
comment on table sys_s3_storage_config is '多云 S3 对象存储凭据与 CDN 加速配置表';
create index if not exists ix_sys_s3_tenant on sys_s3_storage_config(tenant_id, is_default);

-- 8. 推广跟踪分流域名池表
create table if not exists sys_tracking_domain (
    id varchar(64) primary key,
    tenant_id varchar(64) not null default 'public',
    domain varchar(256) not null,
    domain_type varchar(32) not null default 'TRACKING',
    cname_target varchar(256) not null default 'lb-global.affnetwork.com',
    dns_status varchar(32) not null default 'PENDING_CNAME',
    ssl_status varchar(32) not null default 'AUTO_SSL_ACTIVE',
    assigned_affiliate_id varchar(64),
    is_default boolean not null default false,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    constraint uk_sys_domain unique (domain)
);
comment on table sys_tracking_domain is '推广点击跟踪与防红分流域名池表';
create index if not exists ix_sys_domain_lookup on sys_tracking_domain(tenant_id, domain_type, status);
