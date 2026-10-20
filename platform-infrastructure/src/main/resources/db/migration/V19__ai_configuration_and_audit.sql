-- =========================================================================
-- V19: Enterprise AI Multi-Provider Configuration, Prompt Management, and Usage Auditing
-- =========================================================================

CREATE TABLE IF NOT EXISTS ai_model_config (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model_name VARCHAR(64) NOT NULL,
    api_key_encrypted TEXT,
    base_url VARCHAR(256),
    temperature NUMERIC(3,2) DEFAULT 0.70,
    max_tokens INT DEFAULT 2048,
    top_p NUMERIC(3,2) DEFAULT 1.00,
    timeout_seconds INT DEFAULT 30,
    is_default BOOLEAN DEFAULT FALSE,
    active BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_model_config_tenant_active 
    ON ai_model_config(tenant_id, active, priority);

CREATE INDEX IF NOT EXISTS idx_ai_model_config_tenant_default 
    ON ai_model_config(tenant_id, is_default);

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    template_key VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NOT NULL,
    model_preference VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_prompt_template_tenant_key UNIQUE(tenant_id, template_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_prompt_template_lookup 
    ON ai_prompt_template(tenant_id, template_key);

CREATE TABLE IF NOT EXISTS ai_usage_log (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    caller_module VARCHAR(32) NOT NULL,
    model_name VARCHAR(64) NOT NULL,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_log_tenant_time 
    ON ai_usage_log(tenant_id, created_at DESC);
