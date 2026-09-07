-- ===================================================================
-- 模块名称：platform-affiliate (效果营销与商业级网盟核心模块)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. affiliate_partner          : 联盟营销渠道客(Publisher/Affiliate)主表
--   2. affiliate_offer            : 推广计划(Offer)主表 (CPA/CPS/CPI，配额Cap与兜底)
--   3. affiliate_offer_tier_payout: 渠道专属阶梯出价规则表 (VIP渠道高佣金加价)
--   4. affiliate_smart_link       : 智能分流链接(SmartLink/TDS)表
--   5. affiliate_click_session    : 点击追踪会话存根表 (具备30天归因窗口与sub1~sub5)
--   6. affiliate_conversion       : S2S 服务端转化事实表 (订单幂等与CTIT风控质检)
--   7. affiliate_invoice          : 渠道周期性结算发票账单表 (Net-7/15/30出账)
--   8. affiliate_sub_id_stats     : Sub-ID 维度流式统计报表 (实时EPC与CR分析)
-- ===================================================================

-- 1. 联盟营销渠道客主表
create table if not exists affiliate_partner (
    id varchar(64) primary key,                     -- 渠道唯一 ID (如 "aff_888")
    tenant_id varchar(64) not null default 'public',-- 所属租户标识
    name varchar(128) not null,                     -- 渠道主体名称
    status varchar(32) not null default 'ACTIVE',   -- 渠道状态 (ACTIVE, PENDING, SUSPENDED)
    tier varchar(32) not null default 'STANDARD',   -- 渠道等级 (STANDARD, SILVER, GOLD, VIP)
    postback_url_template text,                     -- 下游转化回传宏URL模板
    payment_term varchar(32) not null default 'NET_30', -- 结算账期 (NET_7, NET_15, NET_30)
    min_payout_threshold numeric(12,2) not null default 100.00, -- 最低起提金额门槛 (USD)
    created_at timestamptz not null default now(),  -- 注册入驻时间
    updated_at timestamptz not null default now()   -- 更新时间
);

comment on table affiliate_partner is '联盟营销渠道客(Publisher/Affiliate)主表';
comment on column affiliate_partner.id is '渠道客主键 ID';
comment on column affiliate_partner.tenant_id is '租户标识';
comment on column affiliate_partner.name is '渠道企业或个人名称';
comment on column affiliate_partner.status is '渠道运营状态 (ACTIVE 正常, PENDING 待审, SUSPENDED 封禁)';
comment on column affiliate_partner.tier is '渠道等级评定 (STANDARD 标准, SILVER 白银, GOLD 黄金, VIP 大户)';
comment on column affiliate_partner.postback_url_template is '下游渠道回传宏URL模板 (支持 {click_id}, {payout}, {txid}, {sub1} 等宏)';
comment on column affiliate_partner.payment_term is '结算账期约定 (NET_7, NET_15, NET_30)';
comment on column affiliate_partner.min_payout_threshold is '打款出账最低金额门槛 (USD)';

create index if not exists ix_affiliate_partner_tenant on affiliate_partner(tenant_id, status);

-- 2. 推广计划(Offer)主表
create table if not exists affiliate_offer (
    id varchar(64) primary key,                     -- 推广计划 ID (如 "off_101")
    tenant_id varchar(64) not null default 'public',-- 所属租户
    advertiser_id varchar(64) not null,             -- 所属广告主 ID
    title varchar(256) not null,                    -- 计划名称
    landing_page_url text not null,                 -- 广告主最终落地页模版 (含 {click_id} 宏)
    payout_type varchar(32) not null default 'CPA', -- 计费模式 (CPA, CPL, CPS, CPI, CPC)
    default_payout numeric(12,4) not null default 0.0000,   -- 默认渠道佣金 (USD 或 CPS比例)
    default_revenue numeric(12,4) not null default 0.0000,  -- 默认广告主应收 (USD 或 CPS比例)
    status varchar(32) not null default 'ACTIVE',   -- 计划状态 (ACTIVE 投放中, PAUSED 暂停, EXPIRED 已过期)
    daily_conversion_cap int not null default 0,    -- 每日转化单量上限 Cap (0 代表不限)
    daily_revenue_cap numeric(12,2),                -- 每日消耗预算上限 Cap
    fallback_offer_id varchar(64),                  -- 超限或失效时的保底兜底 Offer ID
    allowed_countries text[],                       -- 允许投放的国家二字码数组 (如 {"US","GB"})
    allowed_devices int[],                          -- 允许投放的设备形态数组 (1=Mobile, 2=Desktop)
    expires_at timestamptz,                         -- 推广计划下线过期时间
    created_at timestamptz not null default now()   -- 创建时间
);

comment on table affiliate_offer is '网盟推广计划(Offer)主表';
comment on column affiliate_offer.id is 'Offer 主键 ID';
comment on column affiliate_offer.tenant_id is '所属租户';
comment on column affiliate_offer.advertiser_id is '所属广告主 ID';
comment on column affiliate_offer.title is '推广计划名称';
comment on column affiliate_offer.landing_page_url is '广告主落地页模版链接 (可含宏变量)';
comment on column affiliate_offer.payout_type is '计费类型 (CPA 单次动作, CPL 线索, CPS 分成, CPI 安装, CPC 点击)';
comment on column affiliate_offer.default_payout is '渠道基准佣金支出';
comment on column affiliate_offer.default_revenue is '广告主基准费用应收';
comment on column affiliate_offer.status is '计划状态 (ACTIVE, PAUSED, EXPIRED)';
comment on column affiliate_offer.daily_conversion_cap is '日单量上限 (0 代表不限，超限跳兜底)';
comment on column affiliate_offer.daily_revenue_cap is '单日预算限额';
comment on column affiliate_offer.fallback_offer_id is '兜底保底 Offer ID (用于溢出重定向)';
comment on column affiliate_offer.allowed_countries is '地域定向国家列表';
comment on column affiliate_offer.allowed_devices is '定向设备类型列表';

create index if not exists ix_affiliate_offer_tenant on affiliate_offer(tenant_id, status);
create index if not exists ix_affiliate_offer_adv on affiliate_offer(tenant_id, advertiser_id);

-- 3. 渠道专属阶梯出价规则表
create table if not exists affiliate_offer_tier_payout (
    id bigserial primary key,                       -- 规则自增主键
    offer_id varchar(64) not null,                  -- 关联推广计划 ID
    affiliate_id varchar(64),                       -- 专属渠道 ID (可选，针对特定渠道定制)
    target_tier varchar(32),                        -- 适用渠道等级 (可选，如 VIP 等级通用加价)
    custom_payout numeric(12,4) not null,           -- 专属定制渠道佣金 (USD)
    custom_revenue numeric(12,4) not null,          -- 专属定制平台应收 (USD)
    created_at timestamptz not null default now()   -- 创建时间
);

comment on table affiliate_offer_tier_payout is '渠道专属阶梯出价规则表';
comment on column affiliate_offer_tier_payout.offer_id is '关联推广计划 ID';
comment on column affiliate_offer_tier_payout.affiliate_id is '定制特价渠道 ID';
comment on column affiliate_offer_tier_payout.target_tier is '目标渠道等级 (VIP, GOLD 等)';
comment on column affiliate_offer_tier_payout.custom_payout is '定制出价佣金';
comment on column affiliate_offer_tier_payout.custom_revenue is '定制平台应收';

create index if not exists ix_offer_tier_lookup on affiliate_offer_tier_payout(offer_id, affiliate_id, target_tier);

-- 4. 智能分流链接(SmartLink/TDS)表
create table if not exists affiliate_smart_link (
    id varchar(64) primary key,                     -- 智能分流链接 ID
    tenant_id varchar(64) not null default 'public',-- 所属租户
    name varchar(128) not null,                     -- 链接名称 (如 "Global E-Commerce SmartLink")
    category varchar(64),                           -- 垂类分类 (E-Commerce, Gaming, Finance)
    target_offer_ids text[] not null,               -- 候选推广计划 ID 数组
    routing_strategy varchar(32) not null default 'HIGHEST_EPC', -- 动态分流策略 (HIGHEST_EPC, ROUND_ROBIN)
    fallback_offer_id varchar(64),                  -- 全局保底兜底 Offer ID
    created_at timestamptz not null default now()   -- 创建时间
);

comment on table affiliate_smart_link is '智能分流链接(SmartLink / TDS)表';
comment on column affiliate_smart_link.id is 'SmartLink 主键 ID';
comment on column affiliate_smart_link.name is '智能链接名称';
comment on column affiliate_smart_link.target_offer_ids is '候选推广计划 ID 集合';
comment on column affiliate_smart_link.routing_strategy is '路由策略 (HIGHEST_EPC 按单点击最高收益分流)';

-- 5. 点击追踪会话存根表
create table if not exists affiliate_click_session (
    click_id varchar(64) primary key,               -- 全局加密唯一 click_id
    tenant_id varchar(64) not null default 'public',-- 租户标识
    offer_id varchar(64) not null,                  -- 点击目标 Offer ID
    affiliate_id varchar(64) not null,              -- 推广来源渠道 ID
    sub1 varchar(128),                              -- 子渠道标签 1 (如广告网络)
    sub2 varchar(128),                              -- 子渠道标签 2 (如广告系列)
    sub3 varchar(128),                              -- 子渠道标签 3 (如创意素材)
    sub4 varchar(128),                              -- 子渠道标签 4 (如展示位置)
    sub5 varchar(128),                              -- 子渠道标签 5 (如自定义参数)
    ip varchar(64),                                 -- 访客 IP
    user_agent text,                                -- 访客浏览器 User-Agent
    country varchar(8),                             -- 国家二字码 (ISO)
    device_type int not null default 1,             -- 设备形态 (1=Phone, 2=Tablet, 3=PC)
    created_at timestamptz not null default now(),  -- 点击发生时间
    expires_at timestamptz not null                 -- 归因窗口失效时间 (默认 30 天)
);

comment on table affiliate_click_session is '点击追踪会话存根表 (归因基础日志)';
comment on column affiliate_click_session.click_id is '全局唯一点击 ID';
comment on column affiliate_click_session.offer_id is '对应 Offer ID';
comment on column affiliate_click_session.affiliate_id is '对应渠道 ID';
comment on column affiliate_click_session.sub1 is '多级渠道追踪参数 sub1';
comment on column affiliate_click_session.expires_at is '归因窗口过期时间戳';

create index if not exists ix_click_session_offer_aff on affiliate_click_session(offer_id, affiliate_id, created_at desc);
create index if not exists ix_click_session_expires on affiliate_click_session(expires_at);

-- 6. S2S 服务端转化事实表
create table if not exists affiliate_conversion (
    id varchar(64) primary key,                     -- 转化全局唯一 ID
    tenant_id varchar(64) not null default 'public',-- 租户标识
    click_id varchar(64) not null,                  -- 关联点击 ID
    tx_id varchar(128) not null,                    -- 广告主端交易订单号 (幂等关键字段)
    offer_id varchar(64) not null,                  -- 关联 Offer ID
    affiliate_id varchar(64) not null,              -- 关联渠道客 ID
    payout numeric(12,4) not null default 0.0000,   -- 渠道客结算佣金 (USD)
    revenue numeric(12,4) not null default 0.0000,  -- 平台广告主应收 (USD)
    sale_amount numeric(12,2) default 0.00,         -- 真实订单交易金额 (CPS场景)
    ctit_seconds bigint not null default 0,         -- 点击至转化时间差 CTIT (秒)
    status varchar(32) not null default 'PENDING',  -- 审核状态 (PENDING 待审, APPROVED 通过, REJECTED 驳回, FRAUD_SUSPECTED 疑似作弊)
    rejection_reason varchar(256),                  -- 驳回或作弊原因 (如 FAST_CONVERSION_CTIT_UNDER_3S)
    created_at timestamptz not null default now()   -- 转化上报发生时间
);

comment on table affiliate_conversion is 'S2S 服务端转化事实表';
comment on column affiliate_conversion.id is '转化流水主键 ID';
comment on column affiliate_conversion.click_id is '关联原始点击 click_id';
comment on column affiliate_conversion.tx_id is '广告主订单流水号 (唯一幂等保障)';
comment on column affiliate_conversion.payout is '渠道应付佣金';
comment on column affiliate_conversion.revenue is '平台应收金额';
comment on column affiliate_conversion.ctit_seconds is 'CTIT 时间差 (秒)';
comment on column affiliate_conversion.status is '审核状态 (PENDING, APPROVED, REJECTED, FRAUD_SUSPECTED)';

-- 严格保证单 Offer 下订单流水号全局唯一，防重刷攻击
create unique index if not exists uk_conversion_offer_tx on affiliate_conversion(offer_id, tx_id);
create index if not exists ix_conversion_aff_status on affiliate_conversion(tenant_id, affiliate_id, status, created_at desc);

-- 7. 渠道周期性结算发票账单表
create table if not exists affiliate_invoice (
    id varchar(64) primary key,                     -- 发票账单 ID (如 "inv_20260901_01")
    tenant_id varchar(64) not null default 'public',-- 租户标识
    affiliate_id varchar(64) not null,              -- 结算渠道客 ID
    amount numeric(12,2) not null check (amount > 0), -- 结算打款金额 (USD)
    conversion_count int not null,                  -- 本期核销转化单量
    payment_term varchar(32) not null,              -- 付款账期 (NET_7, NET_15, NET_30)
    status varchar(32) not null default 'GENERATED',-- 账单状态 (GENERATED 已出账, PAID 已打款, CANCELLED 作废)
    created_at timestamptz not null default now()   -- 出账时间
);

comment on table affiliate_invoice is '渠道营销结算发票账单表';
comment on column affiliate_invoice.id is '账单主键 ID';
comment on column affiliate_invoice.affiliate_id is '结算渠道 ID';
comment on column affiliate_invoice.amount is '应付清算总金额';
comment on column affiliate_invoice.status is '状态 (GENERATED, PAID)';

create index if not exists ix_affiliate_invoice_lookup on affiliate_invoice(tenant_id, affiliate_id, status);

-- 8. Sub-ID 维度流式统计报表
create table if not exists affiliate_sub_id_stats (
    tenant_id varchar(64) not null default 'public',-- 租户标识
    affiliate_id varchar(64) not null,              -- 渠道客 ID
    sub1 varchar(128) not null default 'default',   -- 子渠道追踪维度
    clicks bigint not null default 0,               -- 累计点击总数
    conversions bigint not null default 0,          -- 累计转化总数
    total_payout numeric(14,4) not null default 0,  -- 累计佣金支出 (USD)
    total_revenue numeric(14,4) not null default 0, -- 累计广告营收 (USD)
    epc numeric(10,4) not null default 0,           -- 实时单点击收益 (EPC = payout / clicks)
    cr_percent numeric(8,2) not null default 0,     -- 实时转化率 (CR% = conv / clicks * 100)
    updated_at timestamptz not null default now(),  -- 最后聚合更新时间
    primary key (tenant_id, affiliate_id, sub1)
);

comment on table affiliate_sub_id_stats is 'Sub-ID 维度流式多维统计表';
comment on column affiliate_sub_id_stats.epc is '平均单点击收益 (EPC)';
comment on column affiliate_sub_id_stats.cr_percent is '转化率百分比 (CR%)';
