-- ===================================================================
-- V23: 企业级复式记账不可变分录与财务对账支持 (Double-Entry Bookkeeping Ledger)
-- 包含：ledger_transaction (交易主表), ledger_line (借贷分录明细表)
-- ===================================================================

DO $$
BEGIN
    -- 1. 创建复式记账事务主表 (ledger_transaction)
    EXECUTE 'CREATE TABLE IF NOT EXISTS ledger_transaction (
        id VARCHAR(64) PRIMARY KEY,
        tenant_id VARCHAR(64) NOT NULL DEFAULT ''public'',
        idem_key VARCHAR(128) NOT NULL,
        type VARCHAR(32) NOT NULL,
        reverses_id VARCHAR(64),
        status VARCHAR(16) NOT NULL DEFAULT ''POSTED'',
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_by VARCHAR(64) NOT NULL DEFAULT ''system'',
        CONSTRAINT uq_ledger_txn_tenant_idem UNIQUE (tenant_id, idem_key)
    )';

    -- 2. 创建复式借贷分录明细表 (ledger_line)
    -- direction: 'D' (Debit 借), 'C' (Credit 贷)
    EXECUTE 'CREATE TABLE IF NOT EXISTS ledger_line (
        id BIGSERIAL PRIMARY KEY,
        txn_id VARCHAR(64) NOT NULL REFERENCES ledger_transaction(id) ON DELETE RESTRICT,
        tenant_id VARCHAR(64) NOT NULL DEFAULT ''public'',
        account_id VARCHAR(64) NOT NULL,
        direction CHAR(1) NOT NULL CHECK (direction IN (''D'', ''C'')),
        amount NUMERIC(20, 6) NOT NULL CHECK (amount > 0),
        currency VARCHAR(8) NOT NULL DEFAULT ''USD'',
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    )';

    -- 3. 创建索引加速对账与历史流水审计
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_ledger_line_tenant_acct ON ledger_line (tenant_id, account_id, created_at)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_ledger_line_txn_id ON ledger_line (txn_id)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_ledger_txn_tenant_time ON ledger_transaction (tenant_id, created_at)';

    -- 4. 钱包账户表补充并发版本号 (若尚未存在)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'wallet_account' AND column_name = 'version'
    ) THEN
        EXECUTE 'ALTER TABLE wallet_account ADD COLUMN version BIGINT NOT NULL DEFAULT 0';
    END IF;

END $$;
