package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AntiFraudAuditLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 4D 反作弊审计流水数据访问层 Mapper (Anti-Fraud Audit Log Mapper)
 */
@Mapper
public interface AntiFraudAuditLogMapper extends BaseMapper<AntiFraudAuditLogEntity> {
}
