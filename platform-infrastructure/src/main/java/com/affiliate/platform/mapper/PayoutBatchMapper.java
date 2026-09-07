package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.PayoutBatchEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出海批量打款批次数据访问层 Mapper (Payout Batch Mapper)
 */
@Mapper
public interface PayoutBatchMapper extends BaseMapper<PayoutBatchEntity> {
}
