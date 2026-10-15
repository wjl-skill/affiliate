-- ===================================================================
-- V14: 广告素材审核状态与视频处理任务参数扩展
-- 1. 为 creative 主表补齐素材审核状态 audit_status 与驳回原因 rejection_reason
-- 2. 为 creative_video_process_task 补齐失败错误原因 error_message 与处理参数 operator_params_json
-- ===================================================================

ALTER TABLE IF EXISTS creative ADD COLUMN IF NOT EXISTS audit_status varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW';
ALTER TABLE IF EXISTS creative ADD COLUMN IF NOT EXISTS rejection_reason text;

COMMENT ON COLUMN creative.audit_status IS '素材审核状态 (PENDING_REVIEW, APPROVED, REJECTED)';
COMMENT ON COLUMN creative.rejection_reason IS '素材审核驳回原因明细';

ALTER TABLE IF EXISTS creative_video_process_task ADD COLUMN IF NOT EXISTS error_message text;
ALTER TABLE IF EXISTS creative_video_process_task ADD COLUMN IF NOT EXISTS operator_params_json jsonb DEFAULT '{}';

COMMENT ON COLUMN creative_video_process_task.error_message IS '视频处理执行失败时的异常堆栈或诊断错误信息';
COMMENT ON COLUMN creative_video_process_task.operator_params_json IS '视频处理算子动态参数配置 (如字幕样式、水印坐标)';
