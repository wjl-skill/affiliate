package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AuditEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.UUID;

/**
 * 平台通用安全审计事件 Mapper
 */
@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEventEntity> {
}
