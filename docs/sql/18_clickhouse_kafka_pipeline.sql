-- ===================================================================
-- 18_clickhouse_kafka_pipeline.sql
-- ClickHouse 24+ 实时流数仓架构与 Kafka Engine 摄入管道定义
-- 包含：Kafka 消费流引擎、Materialized View 实时转换、
--       SummingMergeTree 聚合表与 S3 Parquet 冷数据湖仓查询
-- ===================================================================

-- 1. 目标聚合数仓事实表：按小时/日自动合并多维投放指标 (SummingMergeTree)
CREATE TABLE IF NOT EXISTS affiliate_report_daily_olap (
    tenant_id LowCardinality(String) DEFAULT 'default',
    report_date Date,
    offer_id LowCardinality(String),
    affiliate_id LowCardinality(String),
    country LowCardinality(String) DEFAULT 'ZZ',
    sub1 LowCardinality(String) DEFAULT '',
    sub2 LowCardinality(String) DEFAULT '',
    sub3 LowCardinality(String) DEFAULT '',
    sub4 LowCardinality(String) DEFAULT '',
    sub5 LowCardinality(String) DEFAULT '',
    clicks UInt64,
    conversions UInt64,
    payout Decimal64(4),
    revenue Decimal64(4)
) ENGINE = SummingMergeTree((clicks, conversions, payout, revenue))
PARTITION BY toYYYYMM(report_date)
PRIMARY KEY (tenant_id, report_date, offer_id, affiliate_id)
ORDER BY (tenant_id, report_date, offer_id, affiliate_id, country, sub1, sub2, sub3, sub4, sub5)
SETTINGS index_granularity = 8192;

-- 2. Kafka 实时点击事件缓冲消费引擎 (Kafka Engine for Clicks)
CREATE TABLE IF NOT EXISTS kafka_affiliate_click_queue (
    click_id String,
    tenant_id String,
    offer_id String,
    affiliate_id String,
    sub1 Nullable(String),
    sub2 Nullable(String),
    sub3 Nullable(String),
    sub4 Nullable(String),
    sub5 Nullable(String),
    country Nullable(String),
    created_at DateTime64(3, 'UTC')
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'affiliate.events.click',
    kafka_group_name = 'ch_affiliate_click_consumer',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 2,
    kafka_skip_broken_messages = 100;

-- 3. 点击物化视图：从 Kafka 实时提取点击事件并累加至 SummingMergeTree 报表
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_kafka_click_to_olap
TO affiliate_report_daily_olap AS
SELECT
    ifNull(tenant_id, 'default') AS tenant_id,
    toDate(created_at) AS report_date,
    ifNull(offer_id, '') AS offer_id,
    ifNull(affiliate_id, '') AS affiliate_id,
    ifNull(country, 'ZZ') AS country,
    ifNull(sub1, '') AS sub1,
    ifNull(sub2, '') AS sub2,
    ifNull(sub3, '') AS sub3,
    ifNull(sub4, '') AS sub4,
    ifNull(sub5, '') AS sub5,
    toUInt64(1) AS clicks,
    toUInt64(0) AS conversions,
    toDecimal64(0.0000, 4) AS payout,
    toDecimal64(0.0000, 4) AS revenue
FROM kafka_affiliate_click_queue;

-- 4. Kafka 实时转化事件缓冲消费引擎 (Kafka Engine for Conversions)
CREATE TABLE IF NOT EXISTS kafka_affiliate_conversion_queue (
    conversion_id String,
    tenant_id String,
    offer_id String,
    affiliate_id String,
    sub1 Nullable(String),
    sub2 Nullable(String),
    sub3 Nullable(String),
    sub4 Nullable(String),
    sub5 Nullable(String),
    country Nullable(String),
    payout Decimal64(4),
    revenue Decimal64(4),
    status String,
    created_at DateTime64(3, 'UTC')
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'affiliate.events.conversion',
    kafka_group_name = 'ch_affiliate_conv_consumer',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 2,
    kafka_skip_broken_messages = 100;

-- 5. 转化物化视图：仅将 APPROVED / PENDING 转化累加至 SummingMergeTree 报表
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_kafka_conversion_to_olap
TO affiliate_report_daily_olap AS
SELECT
    ifNull(tenant_id, 'default') AS tenant_id,
    toDate(created_at) AS report_date,
    ifNull(offer_id, '') AS offer_id,
    ifNull(affiliate_id, '') AS affiliate_id,
    ifNull(country, 'ZZ') AS country,
    ifNull(sub1, '') AS sub1,
    ifNull(sub2, '') AS sub2,
    ifNull(sub3, '') AS sub3,
    ifNull(sub4, '') AS sub4,
    ifNull(sub5, '') AS sub5,
    toUInt64(0) AS clicks,
    toUInt64(1) AS conversions,
    payout AS payout,
    revenue AS revenue
FROM kafka_affiliate_conversion_queue
WHERE status IN ('APPROVED', 'PENDING');

-- 6. 湖仓一体：S3 Parquet 冷数据直接查询外表 (S3 Table Engine)
-- 支持直接对冷存储归档中的历史千万级 Parquet 点击做即席分析
CREATE TABLE IF NOT EXISTS affiliate_click_archive_s3 (
    click_id String,
    tenant_id LowCardinality(String),
    offer_id LowCardinality(String),
    affiliate_id LowCardinality(String),
    sub1 String,
    sub2 String,
    sub3 String,
    sub4 String,
    sub5 String,
    ip String,
    ua String,
    country LowCardinality(String),
    device_type UInt8,
    created_at DateTime64(3, 'UTC')
) ENGINE = S3(
    'https://s3.amazonaws.com/{bucket_name}/click_archives/*/*.parquet',
    '{access_key_id}',
    '{secret_access_key}',
    'Parquet'
);
