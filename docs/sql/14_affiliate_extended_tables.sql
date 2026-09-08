-- ================================================
-- Affiliate Platform - 数据库表结构（完整版）
-- 包含所有新增服务的表结构定义
-- ================================================

-- ========== API Key Management ==========

CREATE TABLE IF NOT EXISTS affiliate_api_key (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    secret_key VARCHAR(128) NOT NULL UNIQUE,
    scopes TEXT NOT NULL,  -- JSON array: ["READ_OFFERS","WRITE_CONVERSIONS"]
    environment VARCHAR(20) NOT NULL,  -- LIVE, TEST
    status VARCHAR(20) NOT NULL,  -- ACTIVE, DEPRECATED, REVOKED, SUSPENDED
    expires_at TIMESTAMPTZ,
    revoked_reason VARCHAR(500),
    usage_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_api_key_secret ON affiliate_api_key(secret_key);
CREATE INDEX idx_api_key_affiliate ON affiliate_api_key(affiliate_id);
CREATE INDEX idx_api_key_status ON affiliate_api_key(status);
CREATE INDEX idx_api_key_expires ON affiliate_api_key(expires_at);

COMMENT ON TABLE affiliate_api_key IS 'API 密钥管理表';
COMMENT ON COLUMN affiliate_api_key.scopes IS '权限范围 JSON 数组';
COMMENT ON COLUMN affiliate_api_key.usage_count IS 'API 调用次数统计';

-- ========== Payment Gateway ==========

CREATE TABLE IF NOT EXISTS affiliate_payment_method (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,  -- PAYPAL, STRIPE, BANK_TRANSFER, WIRE_TRANSFER, CHECK, CRYPTOCURRENCY
    credentials JSONB NOT NULL,  -- 支付凭证 (加密存储)
    currency VARCHAR(10) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL,  -- PENDING_VERIFICATION, VERIFIED, SUSPENDED, REMOVED
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMPTZ
);

CREATE INDEX idx_payment_method_affiliate ON affiliate_payment_method(affiliate_id);
CREATE INDEX idx_payment_method_status ON affiliate_payment_method(status);

CREATE TABLE IF NOT EXISTS affiliate_payment_transaction (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL,
    invoice_id VARCHAR(64),
    payment_method_id VARCHAR(64) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    fee DECIMAL(12, 2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(12, 2) NOT NULL,
    payout_currency VARCHAR(10) NOT NULL,
    exchange_rate DECIMAL(10, 6) NOT NULL DEFAULT 1.0,
    converted_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,  -- PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    external_payment_id VARCHAR(255),
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ
);

CREATE INDEX idx_payment_tx_affiliate ON affiliate_payment_transaction(affiliate_id);
CREATE INDEX idx_payment_tx_invoice ON affiliate_payment_transaction(invoice_id);
CREATE INDEX idx_payment_tx_status ON affiliate_payment_transaction(status);
CREATE INDEX idx_payment_tx_created ON affiliate_payment_transaction(created_at);

COMMENT ON TABLE affiliate_payment_transaction IS '支付交易流水表';

-- ========== Product Feed ==========

CREATE TABLE IF NOT EXISTS affiliate_product (
    sku VARCHAR(128) PRIMARY KEY,
    offer_id VARCHAR(64) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    price VARCHAR(20) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    image_url VARCHAR(1000),
    product_url VARCHAR(1000),
    brand VARCHAR(200),
    category_id VARCHAR(64),
    availability VARCHAR(20) NOT NULL,  -- IN_STOCK, OUT_OF_STOCK, BACKORDER, DISCONTINUED
    stock_quantity INT NOT NULL DEFAULT 0,
    attributes JSONB,  -- 自定义属性
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_offer ON affiliate_product(offer_id);
CREATE INDEX idx_product_category ON affiliate_product(category_id);
CREATE INDEX idx_product_availability ON affiliate_product(availability);
CREATE INDEX idx_product_updated ON affiliate_product(updated_at);

CREATE TABLE IF NOT EXISTS affiliate_product_feed (
    id VARCHAR(64) PRIMARY KEY,
    offer_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    feed_url VARCHAR(1000) NOT NULL,
    format VARCHAR(20) NOT NULL,  -- CSV, XML, JSON
    update_frequency VARCHAR(20) NOT NULL,  -- HOURLY, DAILY, WEEKLY, MANUAL
    status VARCHAR(20) NOT NULL,  -- PENDING, ACTIVE, PAUSED, ERROR
    product_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_updated_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ
);

CREATE INDEX idx_product_feed_offer ON affiliate_product_feed(offer_id);
CREATE INDEX idx_product_feed_status ON affiliate_product_feed(status);

CREATE TABLE IF NOT EXISTS affiliate_product_category (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_category_id VARCHAR(64),
    product_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_category_parent ON affiliate_product_category(parent_category_id);

COMMENT ON TABLE affiliate_product IS '商品目录表';
COMMENT ON TABLE affiliate_product_feed IS '商品 Feed 同步配置表';

-- ========== Geolocation & IP Intelligence ==========

CREATE TABLE IF NOT EXISTS affiliate_ip_geolocation_cache (
    ip_address VARCHAR(45) PRIMARY KEY,
    country_code VARCHAR(10),
    country_name VARCHAR(100),
    city VARCHAR(100),
    region VARCHAR(100),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    timezone VARCHAR(50),
    isp VARCHAR(255),
    asn VARCHAR(50),
    is_vpn BOOLEAN DEFAULT false,
    is_datacenter BOOLEAN DEFAULT false,
    is_tor BOOLEAN DEFAULT false,
    risk_score INT DEFAULT 0,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ip_geo_expires ON affiliate_ip_geolocation_cache(expires_at);
CREATE INDEX idx_ip_geo_risk ON affiliate_ip_geolocation_cache(risk_score);

COMMENT ON TABLE affiliate_ip_geolocation_cache IS 'IP 地理位置缓存表（减少外部 API 调用）';

-- ========== Attribution ==========

CREATE TABLE IF NOT EXISTS affiliate_touch_point (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128),
    type VARCHAR(20) NOT NULL,  -- IMPRESSION, CLICK, VIEW, ENGAGEMENT
    affiliate_id VARCHAR(64) NOT NULL,
    offer_id VARCHAR(64) NOT NULL,
    click_id VARCHAR(128),
    source VARCHAR(100),
    medium VARCHAR(100),
    campaign VARCHAR(255),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_touchpoint_user ON affiliate_touch_point(user_id, timestamp);
CREATE INDEX idx_touchpoint_affiliate ON affiliate_touch_point(affiliate_id);
CREATE INDEX idx_touchpoint_click ON affiliate_touch_point(click_id);

-- 分区表（按月分区）
CREATE TABLE affiliate_touch_point_y2026m01 PARTITION OF affiliate_touch_point
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE affiliate_touch_point_y2026m02 PARTITION OF affiliate_touch_point
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

CREATE TABLE IF NOT EXISTS affiliate_attribution_result (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    conversion_id VARCHAR(64) NOT NULL UNIQUE,
    conversion_value DECIMAL(12, 2) NOT NULL,
    conversion_time TIMESTAMPTZ NOT NULL,
    attribution_model VARCHAR(50) NOT NULL,  -- FIRST_CLICK, LAST_CLICK, LINEAR, TIME_DECAY, POSITION_BASED
    touch_point_count INT NOT NULL,
    credits JSONB NOT NULL,  -- 归因分配详情
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attribution_user ON affiliate_attribution_result(user_id);
CREATE INDEX idx_attribution_conversion ON affiliate_attribution_result(conversion_id);
CREATE INDEX idx_attribution_time ON affiliate_attribution_result(conversion_time);

COMMENT ON TABLE affiliate_touch_point IS '用户触点历史表（多触点归因）';
COMMENT ON TABLE affiliate_attribution_result IS '归因计算结果表';

-- ========== Notification ==========

CREATE TABLE IF NOT EXISTS affiliate_notification (
    id VARCHAR(64) PRIMARY KEY,
    recipient_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,  -- CONVERSION, PAYMENT, OFFER_UPDATE, SYSTEM, FRAUD_ALERT
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,  -- LOW, NORMAL, HIGH, URGENT
    metadata JSONB,
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_recipient ON affiliate_notification(recipient_id, is_read, created_at DESC);
CREATE INDEX idx_notification_type ON affiliate_notification(type);

CREATE TABLE IF NOT EXISTS affiliate_notification_preference (
    affiliate_id VARCHAR(64) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    enable_email BOOLEAN NOT NULL DEFAULT true,
    enable_sms BOOLEAN NOT NULL DEFAULT false,
    enable_webhook BOOLEAN NOT NULL DEFAULT false,
    enable_in_app BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (affiliate_id, notification_type)
);

CREATE TABLE IF NOT EXISTS affiliate_webhook_endpoint (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    subscribed_types JSONB NOT NULL,  -- ["CONVERSION", "PAYMENT"]
    active BOOLEAN NOT NULL DEFAULT true,
    failure_count INT NOT NULL DEFAULT 0,
    last_failed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_affiliate ON affiliate_webhook_endpoint(affiliate_id);

COMMENT ON TABLE affiliate_notification IS '站内通知表';
COMMENT ON TABLE affiliate_webhook_endpoint IS 'Webhook 订阅配置表';

-- ========== Creative ==========

CREATE TABLE IF NOT EXISTS affiliate_creative (
    id VARCHAR(64) PRIMARY KEY,
    offer_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,  -- BANNER, TEXT_LINK, EMAIL_TEMPLATE, SOCIAL_MEDIA, VIDEO, NATIVE
    name VARCHAR(255) NOT NULL,
    description TEXT,
    assets JSONB NOT NULL,  -- 素材资源 URL 等
    languages JSONB NOT NULL,  -- ["en", "zh"]
    metadata JSONB,
    status VARCHAR(20) NOT NULL,  -- PENDING_REVIEW, APPROVED, REJECTED, PAUSED, ARCHIVED
    reviewer_note TEXT,
    clicks BIGINT NOT NULL DEFAULT 0,
    conversions BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX idx_creative_offer ON affiliate_creative(offer_id);
CREATE INDEX idx_creative_type ON affiliate_creative(type);
CREATE INDEX idx_creative_status ON affiliate_creative(status);

CREATE TABLE IF NOT EXISTS affiliate_creative_performance (
    creative_id VARCHAR(64) PRIMARY KEY,
    impressions BIGINT NOT NULL DEFAULT 0,
    clicks BIGINT NOT NULL DEFAULT 0,
    conversions BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE affiliate_creative IS '素材管理表';

-- ========== Referral Program ==========

CREATE TABLE IF NOT EXISTS affiliate_referral_relationship (
    id VARCHAR(64) PRIMARY KEY,
    referee_id VARCHAR(64) NOT NULL UNIQUE,  -- 被推荐人
    referrer_id VARCHAR(64) NOT NULL,  -- 推荐人
    referral_code VARCHAR(100) NOT NULL,
    tier INT NOT NULL,  -- 推荐层级 (1=直接, 2=二级)
    status VARCHAR(20) NOT NULL,  -- ACTIVE, PAUSED, TERMINATED
    total_commission_earned DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_conversions BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_conversion_at TIMESTAMPTZ
);

CREATE INDEX idx_referral_referee ON affiliate_referral_relationship(referee_id);
CREATE INDEX idx_referral_referrer ON affiliate_referral_relationship(referrer_id);
CREATE INDEX idx_referral_code ON affiliate_referral_relationship(referral_code);

CREATE TABLE IF NOT EXISTS affiliate_referral_commission (
    id VARCHAR(64) PRIMARY KEY,
    referrer_id VARCHAR(64) NOT NULL,
    referee_id VARCHAR(64) NOT NULL,
    conversion_id VARCHAR(64) NOT NULL,
    tier INT NOT NULL,
    commission DECIMAL(12, 2) NOT NULL,
    base_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,  -- PENDING, APPROVED, REJECTED, PAID
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_referral_comm_referrer ON affiliate_referral_commission(referrer_id, status);
CREATE INDEX idx_referral_comm_conversion ON affiliate_referral_commission(conversion_id);

COMMENT ON TABLE affiliate_referral_relationship IS '推荐关系表（二级分销）';
COMMENT ON TABLE affiliate_referral_commission IS '推荐佣金流水表';

-- ========== Compliance ==========

CREATE TABLE IF NOT EXISTS affiliate_terms_acceptance (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL,
    version VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_terms_affiliate ON affiliate_terms_acceptance(affiliate_id, version);

CREATE TABLE IF NOT EXISTS affiliate_tax_document (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL,
    form_type VARCHAR(50) NOT NULL,  -- W9, W8BEN, W8BEN_E, FORM_1099_MISC
    document_url VARCHAR(1000) NOT NULL,
    tax_id VARCHAR(100),
    legal_name VARCHAR(255),
    country VARCHAR(10),
    status VARCHAR(20) NOT NULL,  -- PENDING_REVIEW, APPROVED, REJECTED, EXPIRED
    reviewer_note TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_tax_doc_affiliate ON affiliate_tax_document(affiliate_id);
CREATE INDEX idx_tax_doc_status ON affiliate_tax_document(status);
CREATE INDEX idx_tax_doc_expires ON affiliate_tax_document(expires_at);

CREATE TABLE IF NOT EXISTS affiliate_kyc_verification (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    date_of_birth VARCHAR(20),
    address TEXT,
    id_document_url VARCHAR(1000),
    status VARCHAR(20) NOT NULL,  -- PENDING, VERIFIED, REJECTED, EXPIRED
    rejection_reason TEXT,
    risk_score INT DEFAULT 0,
    initiated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_kyc_affiliate ON affiliate_kyc_verification(affiliate_id);
CREATE INDEX idx_kyc_status ON affiliate_kyc_verification(status);

CREATE TABLE IF NOT EXISTS affiliate_compliance_violation (
    id VARCHAR(64) PRIMARY KEY,
    affiliate_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,  -- FRAUD, TRADEMARK_VIOLATION, COOKIE_STUFFING, etc.
    description TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,  -- LOW, MEDIUM, HIGH, CRITICAL
    evidence TEXT,
    status VARCHAR(50) NOT NULL,  -- OPEN, WARNING_ISSUED, UNDER_INVESTIGATION, RESOLVED
    resolution TEXT,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_violation_affiliate ON affiliate_compliance_violation(affiliate_id, status);
CREATE INDEX idx_violation_severity ON affiliate_compliance_violation(severity);

COMMENT ON TABLE affiliate_compliance_violation IS '合规违规记录表';

-- ========== Performance Report ==========

CREATE TABLE IF NOT EXISTS affiliate_performance_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    dimension VARCHAR(50) NOT NULL,  -- DATE, OFFER, AFFILIATE, COUNTRY, DEVICE
    dimension_value VARCHAR(255) NOT NULL,
    date_range_start DATE NOT NULL,
    date_range_end DATE NOT NULL,
    total_clicks BIGINT NOT NULL DEFAULT 0,
    total_conversions BIGINT NOT NULL DEFAULT 0,
    approved_conversions BIGINT NOT NULL DEFAULT 0,
    conversion_rate DECIMAL(5, 2),
    epc DECIMAL(10, 4),
    total_revenue DECIMAL(12, 2),
    total_payout DECIMAL(12, 2),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_perf_snapshot_type ON affiliate_performance_snapshot(report_type, dimension);
CREATE INDEX idx_perf_snapshot_date ON affiliate_performance_snapshot(date_range_start, date_range_end);

COMMENT ON TABLE affiliate_performance_snapshot IS '效果报表快照表（预聚合）';

-- ========== 视图：实时统计 ==========

CREATE OR REPLACE VIEW v_affiliate_dashboard_stats AS
SELECT
    ap.id AS affiliate_id,
    ap.name AS affiliate_name,
    COUNT(DISTINCT ac.id) AS total_conversions,
    COUNT(DISTINCT CASE WHEN ac.status = 'APPROVED' THEN ac.id END) AS approved_conversions,
    SUM(CASE WHEN ac.status = 'APPROVED' THEN ac.payout ELSE 0 END) AS total_earnings,
    COUNT(DISTINCT acs.id) AS total_clicks
FROM affiliate_partner ap
LEFT JOIN affiliate_conversion ac ON ac.affiliate_id = ap.id
LEFT JOIN affiliate_click_session acs ON acs.affiliate_id = ap.id
GROUP BY ap.id, ap.name;

COMMENT ON VIEW v_affiliate_dashboard_stats IS '渠道仪表盘实时统计视图';
