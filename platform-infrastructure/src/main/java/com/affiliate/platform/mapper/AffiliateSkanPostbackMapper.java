package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AffiliateSkanPostbackEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Apple SKAdNetwork 4.0 归因回传持久化 Mapper
 */
@Mapper
public interface AffiliateSkanPostbackMapper extends BaseMapper<AffiliateSkanPostbackEntity> {
}
