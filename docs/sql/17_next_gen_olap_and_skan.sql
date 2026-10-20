-- ===================================================================
-- 17_next_gen_olap_and_skan.sql
-- 新一代高性能 OLAP 数仓与 Apple SKAdNetwork 4.0 归因表结构定义
-- ===================================================================

-- 1. Apple SKAdNetwork 4.0 归因回传日志与解码事实表 (PostgreSQL)
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

COMMENT ON TABLE affiliate_skan_postback IS 'Apple SKAdNetwork 4.0 归因回传流水与粗细粒度转化解码表';

-- 2. ClickHouse 极速多维 Sub-ID 性能分析表 (ClickHouse DDL)
-- 适用于 ClickHouse 23+ 集群环境，通过 Kafka Engine 实时摄入
/*
CREATE TABLE IF NOT EXISTS affiliate_sub_id_performance_olap (
    tenant_id LowCardinality(String),
    offer_id UInt64,
    aff_id UInt64,
    sub1 LowCardinality(String),
    sub2 LowCardinality(String),
    sub3 LowCardinality(String),
    sub4 LowCardinality(String),
    sub5 LowCardinality(String),
    stat_date Date,
    clicks SimpleAggregateFunction(sum, UInt64),
    conversions SimpleAggregateFunction(sum, UInt64),
    payout SimpleAggregateFunction(sum, Decimal64(4)),
    revenue SimpleAggregateFunction(sum, Decimal64(4)),
    profit SimpleAggregateFunction(sum, Decimal64(4))
) ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(stat_date)
PRIMARY KEY (tenant_id, stat_date, offer_id, aff_id)
ORDER BY (tenant_id, stat_date, offer_id, aff_id, sub1, sub2, sub3, sub4, sub5);
*/
