-- ===================================================================
-- 模块名称：16_platform_creative_automation.sql (素材自动化核心引擎)
-- 适用数据库：PostgreSQL 14+
-- 包含表结构：
--   1. creative_ai_model_meta        : AI 视频模型元数据表
--   2. creative_ai_task_record       : 统一异步生成任务记录表
--   3. creative_ai_billing_ledger    : AI 算力消耗计费分录表
--   4. creative_studio_project       : AI 长视频工作台项目主表
--   5. creative_studio_asset         : AI 长视频资产表 (角色/产品/场景)
--   6. creative_studio_scene         : AI 工作台分镜表 (含Prompt/旁白/关键帧)
--   7. creative_digital_human_avatar : 数字人形象资产库表
--   8. creative_video_process_task   : 视频智能剪辑与混剪任务表
--   9. creative_auto_stitch_config   : 定时批量混剪配置表
--  10. creative_benchmark_7d         : 7日大盘质量基准线快照表
--  11. creative_quality_tag_history  : 素材质量标签历史快照与明细表
--  12. creative_ad_learning_rule     : 广告学习期监控配置与状态表
-- ===================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. AI 视频模型元数据表 (Model Meta)
CREATE TABLE IF NOT EXISTS creative_ai_model_meta (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    model_code VARCHAR(64) NOT NULL,               -- 模型唯一编码 (ark_seedance_2_0, happyhorse_1_1, dashscope_wan_2_1)
    model_name VARCHAR(128) NOT NULL,              -- 展示名称
    provider_code VARCHAR(64) NOT NULL,            -- 供应商编码 (DASHSCOPE, BYTEDANCE_ARK, OPENROUTER, COMFYUI)
    supported_types JSONB NOT NULL DEFAULT '[]',   -- 支持的类型 ["TEXT_TO_VIDEO", "IMAGE_TO_VIDEO", "VIDEO_EDIT"]
    field_schema JSONB NOT NULL DEFAULT '{}',      -- 前端表单动态渲染约束 (分辨率、时长、运镜选项)
    billing_unit VARCHAR(16) NOT NULL DEFAULT 'SECOND', -- SECOND / TOKEN
    unit_price NUMERIC(10, 4) NOT NULL DEFAULT 0.1500, -- 单价 (元/秒 或 元/千Token)
    pricing_multiplier NUMERIC(10, 4) DEFAULT 1.0, -- 计费倍率
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_meta ON creative_ai_model_meta(tenant_id, model_code);

-- 2. 统一异步生成任务记录表 (Task Record)
CREATE TABLE IF NOT EXISTS creative_ai_task_record (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    task_no VARCHAR(64) NOT NULL,                  -- 业务任务单号 (UUID)
    generate_type VARCHAR(32) NOT NULL,            -- 任务类型 (T2V, I2V, STORYBOARD, STITCH, DIGITAL_HUMAN)
    model_code VARCHAR(64) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    remote_task_id VARCHAR(128),                   -- 上游厂商异步任务ID
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, SUCCESS_FETCHED, SUCCESS, FAILED
    prompt TEXT,                                   -- 提示词
    input_assets_json JSONB DEFAULT '{}',          -- 输入资源 (首尾帧、参考图URL)
    raw_output_url TEXT,                           -- 上游返回原始临时下载URL
    oss_url TEXT,                                  -- 转存至自身平台 S3/MinIO OSS 的永久 URL
    duration_seconds INT,
    usage_unit VARCHAR(16) DEFAULT 'SECOND',       -- 计费单位
    usage_quantity NUMERIC(12, 2) DEFAULT 0.00,    -- 实际产生计费量 (如生成5秒)
    estimated_cost NUMERIC(12, 4) DEFAULT 0.0000,  -- 产生费用 (元)
    cost_status VARCHAR(32) DEFAULT 'PENDING',     -- PENDING, CALCULATED, SETTLED
    error_code VARCHAR(64),
    error_message TEXT,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS ix_ai_task_status ON creative_ai_task_record(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_ai_task_no ON creative_ai_task_record(tenant_id, task_no);

-- 3. AI 算力消耗计费分录表 (Billing Ledger)
CREATE TABLE IF NOT EXISTS creative_ai_billing_ledger (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    task_id VARCHAR(64) NOT NULL REFERENCES creative_ai_task_record(id),
    department_id VARCHAR(64),
    project_id VARCHAR(64),
    model_code VARCHAR(64) NOT NULL,
    deducted_amount NUMERIC(12, 4) NOT NULL,       -- 扣减金额
    wallet_balance_after NUMERIC(12, 4) NOT NULL,  -- 扣减后钱包余额
    idempotent_key VARCHAR(128) NOT NULL,          -- 幂等校验键
    settled_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_billing_idempotent ON creative_ai_billing_ledger(tenant_id, idempotent_key);

-- 4. AI 长视频项目表 (Long Video Project)
CREATE TABLE IF NOT EXISTS creative_studio_project (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    title VARCHAR(255) NOT NULL,
    brief TEXT,                                    -- 创意文案与推广诉求
    aspect_ratio VARCHAR(16) DEFAULT '9:16',       -- 画幅比例
    duration_seconds INT DEFAULT 30,               -- 目标总时长
    target_platform VARCHAR(64) DEFAULT 'tiktok',  -- 目标投放渠道
    ad_type VARCHAR(64) DEFAULT 'direct_response', -- 广告类型 (电商带货, IAA小游戏, 应用安装)
    creative_style VARCHAR(64) DEFAULT 'live_action',
    model_code VARCHAR(64) DEFAULT 'ark_seedance_2_0',
    status VARCHAR(32) DEFAULT 'draft',            -- draft, script_ready, generating, completed, failed
    final_video_url TEXT,                          -- 最终合成导出视频 URL
    capcut_draft_url TEXT,                         -- 剪映/CapCut草稿下载包或导入链接
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_studio_project_tenant ON creative_studio_project(tenant_id, created_at DESC);

-- 5. AI 长视频资产表 (Studio Asset: 角色与产品)
CREATE TABLE IF NOT EXISTS creative_studio_asset (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    project_id VARCHAR(64) NOT NULL REFERENCES creative_studio_project(id) ON DELETE CASCADE,
    asset_type VARCHAR(32) NOT NULL,               -- CHARACTER, PRODUCT, SCENE_SETTING
    name VARCHAR(128) NOT NULL,
    description TEXT,
    prompt_data_json JSONB DEFAULT '{}',           -- 用于生成资产图的提示词
    image_url TEXT,                                -- 资产图 OSS 访问链接
    status VARCHAR(32) DEFAULT 'draft',            -- draft, generated, confirmed
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_studio_asset_project ON creative_studio_asset(tenant_id, project_id);

-- 6. AI 工作台分镜表 (Studio Scene)
CREATE TABLE IF NOT EXISTS creative_studio_scene (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    project_id VARCHAR(64) NOT NULL REFERENCES creative_studio_project(id) ON DELETE CASCADE,
    scene_index INT NOT NULL,                      -- 分镜序号 (1, 2, 3...)
    duration_seconds INT DEFAULT 5,                -- 单镜头预估时长 (3~15秒)
    shot_prompt TEXT NOT NULL,                     -- 镜头画面描述 (含末尾「收尾画面：xxx」)
    voiceover_text TEXT,                           -- 旁白文案正文
    voiceover_tone VARCHAR(64) DEFAULT '温暖女声',  -- 旁白音色
    voiceover_speed VARCHAR(16) DEFAULT 'normal',  -- 语速
    subtitle_text TEXT,                            -- 字幕文字 (每行<=15字)
    subtitle_keywords JSONB DEFAULT '[]',          -- 高亮关键词数组
    bgm_cue VARCHAR(255),                          -- BGM三维提示词 (风格+BPM+情绪走向)
    transition_type VARCHAR(32) DEFAULT 'chain_extend', -- hard_cut, chain_extend, match_cut, same_scene
    transition_desc TEXT,                          -- 衔接过渡说明
    start_keyframe_url TEXT,                       -- 起始边界关键帧 URL
    end_keyframe_url TEXT,                         -- 结束边界关键帧 URL
    video_clip_url TEXT,                           -- 当前镜头生成视频片段 OSS URL
    task_id VARCHAR(64),                           -- 关联的生成任务 ID
    status VARCHAR(32) DEFAULT 'draft',            -- draft, generating, generated, failed
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_studio_scene_project ON creative_studio_scene(tenant_id, project_id, scene_index);

-- 7. 数字人形象资产库 (Digital Human Avatar)
CREATE TABLE IF NOT EXISTS creative_digital_human_avatar (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    avatar_name VARCHAR(128) NOT NULL,
    avatar_type VARCHAR(32) NOT NULL DEFAULT '2D_PHOTO', -- 2D_PHOTO, 3D_MODEL
    original_image_url TEXT NOT NULL,              -- 原始人像图 URL
    mask_image_url TEXT,                           -- 抠图主体 Mask URL
    default_speaker VARCHAR(64) DEFAULT 'zh_female_shuangkuai', -- 默认豆包TTS音色
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_digital_avatar ON creative_digital_human_avatar(tenant_id, status);

-- 8. 视频智能剪辑与混剪任务表 (Video Process Task)
CREATE TABLE IF NOT EXISTS creative_video_process_task (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    task_no VARCHAR(64) NOT NULL,
    process_type VARCHAR(32) NOT NULL,             -- INTRO_STITCH, WATERMARK_REMOVE, SUBTITLE, CTA_OVERLAY, BATCH_ASSEMBLE
    source_video_url TEXT NOT NULL,
    intro_video_url TEXT,                          -- 片头素材 (拼接时使用)
    target_width INT,
    target_height INT,
    align_mode VARCHAR(32) DEFAULT 'LETTERBOX',    -- 画幅对齐模式: LETTERBOX, CROP, SCALE
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, SUCCESS, FAILED
    output_video_url TEXT,
    output_cover_url TEXT,
    duration_seconds NUMERIC(10, 2),
    file_size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS ix_process_task ON creative_video_process_task(tenant_id, status);

-- 9. 定时批量混剪配置表 (Auto Stitch Config)
CREATE TABLE IF NOT EXISTS creative_auto_stitch_config (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    config_name VARCHAR(128) NOT NULL,
    folder_sources JSONB NOT NULL DEFAULT '[]',     -- 来源物料文件夹集合 (含片头组与主体组)
    target_aspect_ratio VARCHAR(16) DEFAULT '9:16',
    align_mode VARCHAR(32) DEFAULT 'LETTERBOX',
    cta_config JSONB DEFAULT '{}',
    scheduled_time TIMESTAMPTZ,                    -- 计划执行时间
    status VARCHAR(32) DEFAULT 'PENDING',          -- PENDING, EXECUTED, CANCELLED
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_auto_stitch_schedule ON creative_auto_stitch_config(tenant_id, status, scheduled_time);

-- 10. 7日大盘质量基准线快照表 (Benchmark 7D)
CREATE TABLE IF NOT EXISTS creative_benchmark_7d (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    stat_date DATE NOT NULL,                       -- 统计日期 (基准产生日)
    window_start_date DATE NOT NULL,               -- 滑动窗口开始 (stat_date - 6)
    window_end_date DATE NOT NULL,                 -- 滑动窗口结束 (stat_date)
    material_sample_count INT NOT NULL,            -- 参与基准统计的有效样本数
    sample_cost_threshold NUMERIC(12, 2) DEFAULT 100.00,
    sample_click_threshold BIGINT DEFAULT 50,
    sample_install_threshold BIGINT DEFAULT 5,
    roas_p75 NUMERIC(10, 4) NOT NULL,              -- 明星素材及格线
    roas_p25 NUMERIC(10, 4) NOT NULL,              -- 风险警戒线
    cost_p50 NUMERIC(12, 2) NOT NULL,              -- 放量门槛中位数
    cpi_avg NUMERIC(10, 4) NOT NULL,
    ctr_avg NUMERIC(10, 4) NOT NULL,
    cvr_install_avg NUMERIC(10, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_benchmark_stat ON creative_benchmark_7d(tenant_id, stat_date);

-- 11. 素材质量标签历史快照与明细表 (Quality Tag History)
CREATE TABLE IF NOT EXISTS creative_quality_tag_history (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    creative_id VARCHAR(64) NOT NULL,              -- 对应 platform-creative 的 creative.id
    stat_date DATE NOT NULL,
    quality_level VARCHAR(32) NOT NULL,            -- UNRATED, STAR, POTENTIAL, CORE, RISK, BLACKHOLE
    score NUMERIC(5, 2) NOT NULL,                  -- 评级综合分
    promotion_cost_7d NUMERIC(12, 2) NOT NULL,     -- 7天实际消耗
    clicks_7d BIGINT NOT NULL,
    installs_7d BIGINT NOT NULL,
    roas_7d NUMERIC(10, 4) NOT NULL,
    cpi_7d NUMERIC(10, 4) NOT NULL,
    ctr_7d NUMERIC(10, 4) NOT NULL,
    reason_tags JSONB DEFAULT '[]',                -- 命中规则原因 (如 ["HIGH_ROAS", "LOW_CPI", "HIGH_COST"])
    benchmark_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_quality_tag_creative ON creative_quality_tag_history(tenant_id, creative_id, stat_date DESC);
CREATE INDEX IF NOT EXISTS ix_quality_tag_level ON creative_quality_tag_history(tenant_id, stat_date, quality_level);

-- 12. 广告学习期监控配置与状态表 (Ad Learning Rule)
CREATE TABLE IF NOT EXISTS creative_ad_learning_rule (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',
    entity_level VARCHAR(32) NOT NULL,             -- CAMPAIGN, ADSET, AD
    entity_id VARCHAR(64) NOT NULL,                -- 三方平台广告ID (Facebook/TikTok)
    entity_name VARCHAR(255),
    channel_code VARCHAR(32) NOT NULL,             -- FACEBOOK, TIKTOK, GOOGLE
    time_days INT NOT NULL DEFAULT 2,              -- 学习期天数门槛
    cost_threshold NUMERIC(12, 2) NOT NULL DEFAULT 50.00, -- 学习期消耗门槛
    first_metric_time TIMESTAMPTZ,                 -- 产生首次消耗事实的时间
    cumulative_cost NUMERIC(12, 2) DEFAULT 0.00,
    is_learning BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_learning_rule ON creative_ad_learning_rule(tenant_id, entity_level, entity_id);
