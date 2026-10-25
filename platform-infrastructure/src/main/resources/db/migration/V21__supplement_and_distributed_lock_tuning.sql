-- ===================================================================
-- V21__supplement_and_distributed_lock_tuning.sql
-- 生产级索引优化：针对 Offer 流量补量调度、MMP 推送审计与高并发点击会话加固
-- ===================================================================

-- 1. 加速后台定时自动补量调度器巡检已启用的活跃规则 (避免全表扫描)
CREATE INDEX IF NOT EXISTS idx_supp_rule_active_auto 
    ON affiliate_offer_supplement_rule (status, auto_enabled, tenant_id);

-- 2. 加速基于特定补量规则反查历史流水与成功率分析
CREATE INDEX IF NOT EXISTS idx_supp_log_rule_created 
    ON affiliate_offer_supplement_log (rule_id, created_at DESC);

-- 3. 加速多租户维度点击会话生命周期与冷热数据分析
CREATE INDEX IF NOT EXISTS idx_click_session_tenant_created 
    ON affiliate_click_session (tenant_id, created_at DESC);
