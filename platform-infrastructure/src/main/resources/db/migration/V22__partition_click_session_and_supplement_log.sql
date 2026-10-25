-- ===================================================================
-- V22: PostgreSQL 范围分区与历史数据定期归档存储过程 (Range Partitioning & Archival)
-- 适用高并发海量流水表：affiliate_click_session & affiliate_offer_supplement_log
-- ===================================================================

DO $$
BEGIN
    -- 1. 创建按 created_at 范围分区的点击会话分区表结构
    EXECUTE 'CREATE TABLE IF NOT EXISTS affiliate_click_session_partitioned (
        click_id VARCHAR(64) NOT NULL,
        tenant_id VARCHAR(64) NOT NULL DEFAULT ''public'',
        offer_id VARCHAR(64) NOT NULL,
        affiliate_id VARCHAR(64) NOT NULL,
        sub1 VARCHAR(128),
        sub2 VARCHAR(128),
        sub3 VARCHAR(128),
        sub4 VARCHAR(128),
        sub5 VARCHAR(128),
        ip VARCHAR(64),
        user_agent TEXT,
        country VARCHAR(8),
        device_type INTEGER NOT NULL DEFAULT 1,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
        PRIMARY KEY (click_id, created_at)
    ) PARTITION BY RANGE (created_at)';

    -- 预挂载 2026 年各月度分区及兜底分区
    FOR m IN 1..12 LOOP
        DECLARE
            s_date text := format('2026-%s-01 00:00:00+00', lpad(m::text, 2, '0'));
            e_date text := CASE WHEN m = 12 THEN '2027-01-01 00:00:00+00'
                                ELSE format('2026-%s-01 00:00:00+00', lpad((m + 1)::text, 2, '0')) END;
            p_name text := format('affiliate_click_session_y2026m%s', lpad(m::text, 2, '0'));
        BEGIN
            EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF affiliate_click_session_partitioned FOR VALUES FROM (%L) TO (%L)',
                           p_name, s_date, e_date);
        END;
    END LOOP;
    EXECUTE 'CREATE TABLE IF NOT EXISTS affiliate_click_session_part_default PARTITION OF affiliate_click_session_partitioned DEFAULT';

    -- 2. 创建按 created_at 范围分区的 Offer 流量补量流水表结构
    EXECUTE 'CREATE TABLE IF NOT EXISTS affiliate_offer_supplement_log_partitioned (
        id VARCHAR(64) NOT NULL,
        tenant_id VARCHAR(64) NOT NULL DEFAULT ''default'',
        task_batch_id VARCHAR(64) NOT NULL,
        rule_id VARCHAR(64),
        offer_id VARCHAR(64) NOT NULL,
        affiliate_id VARCHAR(64) NOT NULL,
        click_id VARCHAR(64) NOT NULL,
        mmp_platform VARCHAR(32) NOT NULL DEFAULT ''APPSFLYER'',
        profile_id VARCHAR(128),
        idfa VARCHAR(128),
        gadvid VARCHAR(128),
        ip VARCHAR(64),
        user_agent TEXT,
        os VARCHAR(32),
        ds_publisher_id VARCHAR(128),
        ds_adx VARCHAR(64),
        source_tag VARCHAR(64),
        model_flag INTEGER,
        target_url TEXT NOT NULL,
        http_status INTEGER NOT NULL DEFAULT 200,
        success BOOLEAN NOT NULL DEFAULT TRUE,
        response_summary TEXT,
        latency_ms BIGINT NOT NULL DEFAULT 0,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id, created_at)
    ) PARTITION BY RANGE (created_at)';

    FOR m IN 1..12 LOOP
        DECLARE
            s_date text := format('2026-%s-01 00:00:00+00', lpad(m::text, 2, '0'));
            e_date text := CASE WHEN m = 12 THEN '2027-01-01 00:00:00+00'
                                ELSE format('2026-%s-01 00:00:00+00', lpad((m + 1)::text, 2, '0')) END;
            p_name text := format('affiliate_offer_supplement_log_y2026m%s', lpad(m::text, 2, '0'));
        BEGIN
            EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF affiliate_offer_supplement_log_partitioned FOR VALUES FROM (%L) TO (%L)',
                           p_name, s_date, e_date);
        END;
    END LOOP;
    EXECUTE 'CREATE TABLE IF NOT EXISTS affiliate_offer_supplement_log_part_default PARTITION OF affiliate_offer_supplement_log_partitioned DEFAULT';

    -- 3. 历史冷归档表
    EXECUTE 'CREATE TABLE IF NOT EXISTS affiliate_click_session_archive (
        click_id VARCHAR(64) NOT NULL,
        tenant_id VARCHAR(64) NOT NULL,
        offer_id VARCHAR(64) NOT NULL,
        affiliate_id VARCHAR(64) NOT NULL,
        sub1 VARCHAR(128),
        sub2 VARCHAR(128),
        sub3 VARCHAR(128),
        sub4 VARCHAR(128),
        sub5 VARCHAR(128),
        ip VARCHAR(64),
        user_agent TEXT,
        country VARCHAR(8),
        device_type INTEGER NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
        expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
        archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (click_id, created_at)
    )';

    EXECUTE 'CREATE TABLE IF NOT EXISTS affiliate_offer_supplement_log_archive (
        id VARCHAR(64) NOT NULL,
        tenant_id VARCHAR(64) NOT NULL,
        task_batch_id VARCHAR(64) NOT NULL,
        rule_id VARCHAR(64),
        offer_id VARCHAR(64) NOT NULL,
        affiliate_id VARCHAR(64) NOT NULL,
        click_id VARCHAR(64) NOT NULL,
        mmp_platform VARCHAR(32) NOT NULL,
        profile_id VARCHAR(128),
        idfa VARCHAR(128),
        gadvid VARCHAR(128),
        ip VARCHAR(64),
        user_agent TEXT,
        os VARCHAR(32),
        ds_publisher_id VARCHAR(128),
        ds_adx VARCHAR(64),
        source_tag VARCHAR(64),
        model_flag INTEGER,
        target_url TEXT NOT NULL,
        http_status INTEGER NOT NULL,
        success BOOLEAN NOT NULL,
        response_summary TEXT,
        latency_ms BIGINT NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
        archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id, created_at)
    )';
END $$;

-- 4. 自动化分区创建与维护存储过程 (Automated Monthly Partition Provisioner)
CREATE OR REPLACE FUNCTION create_monthly_partition(p_table text, p_year int, p_month int)
RETURNS text AS $$
DECLARE
    start_date text;
    end_date text;
    part_name text;
    sql_stmt text;
    next_year int;
    next_month int;
BEGIN
    IF p_month = 12 THEN
        next_month := 1;
        next_year := p_year + 1;
    ELSE
        next_month := p_month + 1;
        next_year := p_year;
    END IF;

    start_date := format('%s-%s-01 00:00:00+00', p_year, lpad(p_month::text, 2, '0'));
    end_date := format('%s-%s-01 00:00:00+00', next_year, lpad(next_month::text, 2, '0'));
    part_name := format('%s_y%sm%s', p_table, p_year, lpad(p_month::text, 2, '0'));

    sql_stmt := format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        part_name, p_table, start_date, end_date
    );
    EXECUTE sql_stmt;
    RETURN format('Created or confirmed partition %s', part_name);
END;
$$ LANGUAGE plpgsql;

-- 5. 超期历史数据定期归档与清理存储过程 (Data Retention Archival Procedure)
CREATE OR REPLACE FUNCTION archive_partitioned_records(p_retention_days integer DEFAULT 90)
RETURNS TABLE(archived_click_sessions bigint, archived_supplement_logs bigint, cutoff_timestamp timestamp with time zone) AS $$
DECLARE
    cutoff timestamptz;
    click_cnt bigint := 0;
    supp_cnt bigint := 0;
BEGIN
    cutoff := CURRENT_TIMESTAMP - (p_retention_days || ' days')::interval;

    -- 归档点击会话
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'affiliate_click_session') THEN
        WITH moved AS (
            DELETE FROM affiliate_click_session
            WHERE created_at < cutoff
            RETURNING click_id, tenant_id, offer_id, affiliate_id, sub1, sub2, sub3, sub4, sub5,
                      ip, user_agent, country, device_type, created_at, expires_at
        )
        INSERT INTO affiliate_click_session_archive (
            click_id, tenant_id, offer_id, affiliate_id, sub1, sub2, sub3, sub4, sub5,
            ip, user_agent, country, device_type, created_at, expires_at
        )
        SELECT * FROM moved;
        GET DIAGNOSTICS click_cnt = ROW_COUNT;
    END IF;

    -- 归档补量流水
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'affiliate_offer_supplement_log') THEN
        WITH moved_supp AS (
            DELETE FROM affiliate_offer_supplement_log
            WHERE created_at < cutoff
            RETURNING id, tenant_id, task_batch_id, rule_id, offer_id, affiliate_id, click_id,
                      mmp_platform, profile_id, idfa, gadvid, ip, user_agent, os,
                      ds_publisher_id, ds_adx, source_tag, model_flag, target_url,
                      http_status, success, response_summary, latency_ms, created_at
        )
        INSERT INTO affiliate_offer_supplement_log_archive (
            id, tenant_id, task_batch_id, rule_id, offer_id, affiliate_id, click_id,
            mmp_platform, profile_id, idfa, gadvid, ip, user_agent, os,
            ds_publisher_id, ds_adx, source_tag, model_flag, target_url,
            http_status, success, response_summary, latency_ms, created_at
        )
        SELECT * FROM moved_supp;
        GET DIAGNOSTICS supp_cnt = ROW_COUNT;
    END IF;

    RETURN QUERY SELECT click_cnt, supp_cnt, cutoff;
END;
$$ LANGUAGE plpgsql;
