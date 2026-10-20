package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AiUsageLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用与 Token 消耗审计流水数据访问层 Mapper (AI Usage Log Mapper)
 */
@Mapper
public interface AiUsageLogMapper extends BaseMapper<AiUsageLogEntity> {
}
