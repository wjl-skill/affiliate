-- ===================================================================
-- V10: affiliate_conversion 列名与 ConversionEntity 实体对齐
-- 将历史列名 tx_id 重命名为 transaction_id (MyBatis-Plus 驼峰默认映射)
-- ===================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'affiliate_conversion' AND column_name = 'tx_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'affiliate_conversion' AND column_name = 'transaction_id') THEN
        ALTER TABLE affiliate_conversion RENAME COLUMN tx_id TO transaction_id;
    END IF;
END $$;

-- 幂等唯一索引跟随列名重建，保障单 Offer 下订单流水号全局唯一
DROP INDEX IF EXISTS uk_conversion_offer_tx;
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversion_offer_tx ON affiliate_conversion(offer_id, transaction_id);

-- 转化来源 Sub-1 冗余列 (归因自点击会话) 与下游 Postback 回传执行状态
ALTER TABLE affiliate_conversion ADD COLUMN IF NOT EXISTS sub1 varchar(128);
ALTER TABLE affiliate_conversion ADD COLUMN IF NOT EXISTS postback_status varchar(32) NOT NULL DEFAULT 'PENDING';

comment on column affiliate_conversion.sub1 is '流量来源 Sub-1 标识 (归因自点击会话)';
comment on column affiliate_conversion.postback_status is '渠道 Postback 回传状态 (PENDING 未回传, DELIVERED 已回传, FAILED 回传失败)';
