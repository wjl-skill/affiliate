package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.PayoutItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出海批量打款明细清单数据访问层 Mapper (Payout Item Mapper)
 */
@Mapper
public interface PayoutItemMapper extends BaseMapper<PayoutItemEntity> {
}
