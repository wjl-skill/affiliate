package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AntiFraudBlacklistEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 4D 反作弊风控黑名单数据访问层 Mapper (Anti-Fraud Blacklist Mapper)
 */
@Mapper
public interface AntiFraudBlacklistMapper extends BaseMapper<AntiFraudBlacklistEntity> {
}
