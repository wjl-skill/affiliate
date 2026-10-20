-- ===================================================================
-- V18__next_gen_skan_and_agent.sql
-- 新一代 Apple SKAdNetwork 4.0 归因流水与创意自治 Agent 执行流水表
-- ===================================================================

-- 1. Apple SKAdNetwork 4.0 归因回传日志与解码事实表
CREATE TABLE IF NOT EXISTS affiliate_skan_postback (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    transaction_id VARCHAR(128) NOT NULL,
    ad_network_id VARCHAR(64) NOT NULL,
    source_identifier VARCHAR(16) NOT NULL,
    campaign_id VARCHAR(64),
    postback_sequence_index SMALLINT NOT NULL DEFAULT 0,
    conversion_value_fine INTEGER,
    conversion_value_coarse VARCHAR(16),
    estimated_revenue NUMERIC(14, 4) NOT NULL DEFAULT 0.0000,
    fidelity_type SMALLINT NOT NULL DEFAULT 1,
    skan_version VARCHAR(16) NOT NULL DEFAULT '4.0',
    app_id VARCHAR(128),
    attribution_signature TEXT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSED',
    CONSTRAINT uq_skan_tx_seq UNIQUE (tenant_id, transaction_id, postback_sequence_index)
);

CREATE INDEX IF NOT EXISTS idx_skan_tenant_campaign ON affiliate_skan_postback (tenant_id, campaign_id, received_at);
CREATE INDEX IF NOT EXISTS idx_skan_ad_network ON affiliate_skan_postback (tenant_id, ad_network_id, received_at);

-- 2. 营销创意自治 Agent 调优执行流水表
CREATE TABLE IF NOT EXISTS creative_agent_action (
    id BIGSERIAL PRIMARY KEY,
    action_id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    material_id VARCHAR(64) NOT NULL,
    original_project_id VARCHAR(64),
    new_project_id VARCHAR(64),
    revised_prompt TEXT,
    refined_opening_hook TEXT,
    refined_cta TEXT,
    capcut_draft_path TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DIAGNOSED',
    execution_log TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_action_material ON creative_agent_action (tenant_id, material_id, created_at);
