-- ===================================================================
-- V25: 分布式 LinUCB 上下文多臂老虎机模型参数持久化表 (Distributed LinUCB Bandit Arm Model)
-- ===================================================================

CREATE TABLE IF NOT EXISTS affiliate_bandit_arm (
    arm_key          VARCHAR(192) PRIMARY KEY,
    tenant_id        VARCHAR(64) NOT NULL DEFAULT 'default',
    smart_link_id    VARCHAR(64) NOT NULL DEFAULT 'global',
    offer_id         VARCHAR(64) NOT NULL,
    dimension        INTEGER NOT NULL DEFAULT 12,
    a_inv_json       TEXT NOT NULL,
    b_vec_json       TEXT NOT NULL,
    sample_count     INTEGER NOT NULL DEFAULT 0,
    clicks           INTEGER NOT NULL DEFAULT 0,
    conversions      INTEGER NOT NULL DEFAULT 0,
    total_reward     NUMERIC(16, 6) NOT NULL DEFAULT 0.000000,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_bandit_arm_tenant_smartlink ON affiliate_bandit_arm (tenant_id, smart_link_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS ix_bandit_arm_offer ON affiliate_bandit_arm (tenant_id, offer_id);
