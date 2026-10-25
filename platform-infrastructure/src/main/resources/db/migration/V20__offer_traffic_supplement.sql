-- ===================================================================
-- V20__offer_traffic_supplement.sql
-- Offer 流量补量规则配置表与归因平台 (AppsFlyer / Adjust) 点击补量推送日志表
-- ===================================================================

-- 1. Offer 级补量规则与存储桶数据源配置表
CREATE TABLE IF NOT EXISTS affiliate_offer_supplement_rule (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    offer_id VARCHAR(64) NOT NULL,
    affiliate_id VARCHAR(64) NOT NULL DEFAULT 'aff_supplement',
    mmp_platform VARCHAR(32) NOT NULL DEFAULT 'APPSFLYER',
    tracking_url_template TEXT,
    storage_config_id VARCHAR(64),
    object_key_or_path TEXT NOT NULL,
    os_filter VARCHAR(16) NOT NULL DEFAULT 'ALL',
    batch_size INTEGER NOT NULL DEFAULT 50,
    qps_limit INTEGER NOT NULL DEFAULT 20,
    auto_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cron_interval_minutes INTEGER NOT NULL DEFAULT 30,
    last_cursor_offset INTEGER NOT NULL DEFAULT 0,
    total_dispatched_clicks BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_executed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supp_rule_tenant_offer ON affiliate_offer_supplement_rule (tenant_id, offer_id);

-- 2. Offer 补量点击生成与 MMP 归因推送明细表
CREATE TABLE IF NOT EXISTS affiliate_offer_supplement_log (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    task_batch_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64),
    offer_id VARCHAR(64) NOT NULL,
    affiliate_id VARCHAR(64) NOT NULL,
    click_id VARCHAR(64) NOT NULL,
    mmp_platform VARCHAR(32) NOT NULL DEFAULT 'APPSFLYER',
    profile_id VARCHAR(128),
    idfa VARCHAR(128),
    gadvid VARCHAR(128),
    ip VARCHAR(64),
    user_agent TEXT,
    os VARCHAR(32),
    ds_publisher_id VARCHAR(128),
    ds_adx VARCHAR(64),
    source_tag VARCHAR(64),
    model_flag INTEGER,
    target_url TEXT NOT NULL,
    http_status INTEGER NOT NULL DEFAULT 200,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    response_summary TEXT,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supp_log_tenant_offer ON affiliate_offer_supplement_log (tenant_id, offer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_supp_log_batch ON affiliate_offer_supplement_log (task_batch_id);
CREATE INDEX IF NOT EXISTS idx_supp_log_click ON affiliate_offer_supplement_log (click_id);
