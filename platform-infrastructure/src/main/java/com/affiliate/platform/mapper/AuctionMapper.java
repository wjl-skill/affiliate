package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AuctionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实时竞价拍卖成交事实数据访问层 Mapper (Auction Mapper)
 */
@Mapper
public interface AuctionMapper extends BaseMapper<AuctionEntity> {
}
