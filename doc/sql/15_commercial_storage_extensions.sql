-- ====================================================================
-- Section 15: 商业级全业务模块扩展与数据持久化体系
-- 包含: 多事件转化目标、4D反作弊风控黑名单与审计流、出海批量打款批次与清单、
--       外汇实时点差汇率表、CDP用户行为画像状态、Cohort留存衰减获客与收益流水
-- ====================================================================

-- 1. 推广计划多事件漏斗转化目标表 (Offer Goals)
CREATE TABLE IF NOT EXISTS affiliate_offer_goal (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    offer_id VARCHAR(64) NOT NULL,
    goal_name VARCHAR(128) NOT NULL,
    goal_type VARCHAR(32) NOT NULL,
    payout_type VARCHAR(32) NOT NULL DEFAULT 'FLAT',
    payout NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    revenue NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_offer_goal_offer ON affiliate_offer_goal(tenant_id, offer_id, status);

-- 2. 4D 反作弊动态风控黑名单表 (Anti-Fraud Blacklist)
CREATE TABLE IF NOT EXISTS affiliate_antifraud_blacklist (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL, -- 'IP', 'SUB_ID', 'AFFILIATE_ID'
    target_value VARCHAR(128) NOT NULL,
    reason VARCHAR(255),
    operator VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_antifraud_bl ON affiliate_antifraud_blacklist(tenant_id, target_type, target_value, status);

-- 3. 4D 反作弊作弊拦截与风控审计流水表 (Anti-Fraud Audit Log)
CREATE TABLE IF NOT EXISTS affiliate_antifraud_audit_log (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(128),
    click_id VARCHAR(128),
    affiliate_id VARCHAR(64),
    ip VARCHAR(64),
    ctit_seconds NUMERIC(10,2),
    risk_score INTEGER NOT NULL DEFAULT 0,
    primary_reason VARCHAR(128),
    action VARCHAR(32) NOT NULL, -- 'REJECTED', 'FLAGGED', 'APPROVED'
    details_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_antifraud_audit ON affiliate_antifraud_audit_log(tenant_id, created_at DESC);

-- 4. 出海批量打款结算批次表 (Mass Payout Batch Header)
CREATE TABLE IF NOT EXISTS billing_payout_batch (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    batch_number VARCHAR(64) NOT NULL,
    payment_method VARCHAR(32) NOT NULL, -- 'TIPALTI_WIRE', 'PAYONEER_MASS', 'PAYPAL_API', 'CRYPTO_USDT'
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', -- 'DRAFT', 'PROCESSING', 'DISBURSED', 'FAILED'
    item_count INTEGER NOT NULL DEFAULT 0,
    total_gross_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    total_tax_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    total_net_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    disbursed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payout_batch ON billing_payout_batch(tenant_id, batch_number);

-- 5. 出海批量打款明细清单从表 (Mass Payout Item Line)
CREATE TABLE IF NOT EXISTS billing_payout_item (
    id VARCHAR(64) PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    affiliate_id VARCHAR(64) NOT NULL,
    beneficiary_name VARCHAR(128),
    tax_id VARCHAR(64),
    tax_rate NUMERIC(6,4) NOT NULL DEFAULT 0.0000,
    gross_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    tax_usd NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(16) NOT NULL DEFAULT 'USD',
    fx_rate NUMERIC(12,6) NOT NULL DEFAULT 1.000000,
    target_amount NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    method VARCHAR(32) NOT NULL,
    account VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payout_item ON billing_payout_item(batch_id, affiliate_id);

-- 6. 外汇实时点差汇率表 (Currency FX Rates)
CREATE TABLE IF NOT EXISTS billing_currency_fx_rate (
    id VARCHAR(64) PRIMARY KEY,
    source_currency VARCHAR(16) NOT NULL DEFAULT 'USD',
    target_currency VARCHAR(16) NOT NULL,
    base_rate NUMERIC(12,6) NOT NULL,
    spread_rate NUMERIC(6,4) NOT NULL DEFAULT 0.0150,
    effective_rate NUMERIC(12,6) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_fx_pair ON billing_currency_fx_rate(source_currency, target_currency);

-- 7. CDP 客户实时行为聚合画像快照表 (CDP User Traits)
CREATE TABLE IF NOT EXISTS cdp_user_trait_state (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    primary_id VARCHAR(128) NOT NULL,
    total_spend NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    purchase_count INTEGER NOT NULL DEFAULT 0,
    click_count INTEGER NOT NULL DEFAULT 0,
    pageview_count INTEGER NOT NULL DEFAULT 0,
    events_7d_count INTEGER NOT NULL DEFAULT 0,
    preferred_category VARCHAR(64),
    preferred_device VARCHAR(32),
    traits_json TEXT,
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cdp_user ON cdp_user_trait_state(tenant_id, primary_id);

-- 8. Cohort 留存分析用户获客支出表 (Cohort Acquisition)
CREATE TABLE IF NOT EXISTS report_cohort_acquisition (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    cohort_date DATE NOT NULL,
    cost NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cohort_acq ON report_cohort_acquisition(tenant_id, cohort_date);

-- 9. Cohort 留存分析用户回访创收流水表 (Cohort Activity)
CREATE TABLE IF NOT EXISTS report_cohort_activity (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    activity_date DATE NOT NULL,
    revenue NUMERIC(16,4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cohort_act ON report_cohort_activity(tenant_id, user_id, activity_date);
