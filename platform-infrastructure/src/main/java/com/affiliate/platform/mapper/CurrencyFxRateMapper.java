package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.CurrencyFxRateEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外汇汇率与点差数据访问层 Mapper (Currency FX Rate Mapper)
 */
@Mapper
public interface CurrencyFxRateMapper extends BaseMapper<CurrencyFxRateEntity> {
}
