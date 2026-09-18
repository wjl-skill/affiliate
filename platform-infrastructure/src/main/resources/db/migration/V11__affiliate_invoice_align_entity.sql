-- ===================================================================
-- V11: affiliate_invoice 列与 AffiliateInvoiceEntity 实体对齐
-- 补齐实体已有但表缺失的 billing_cycle / paid_at 列，
-- 并为历史库补建 conversion_count (本期核销转化单量快照) 兜底列
-- ===================================================================

ALTER TABLE affiliate_invoice ADD COLUMN IF NOT EXISTS billing_cycle varchar(64) NOT NULL DEFAULT '';
ALTER TABLE affiliate_invoice ADD COLUMN IF NOT EXISTS paid_at timestamptz;
ALTER TABLE affiliate_invoice ADD COLUMN IF NOT EXISTS conversion_count int4 NOT NULL DEFAULT 0;

comment on column affiliate_invoice.billing_cycle is '结算账期周期标识 (如 CYCLE_2026-09)';
comment on column affiliate_invoice.conversion_count is '出账时快照的核销转化单量';
comment on column affiliate_invoice.paid_at is '确认打款时间 (未打款为空)';
