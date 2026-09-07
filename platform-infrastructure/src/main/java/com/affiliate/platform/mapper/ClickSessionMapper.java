package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.ClickSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点击追踪会话数据访问层 Mapper (Click Session Mapper)
 */
@Mapper
public interface ClickSessionMapper extends BaseMapper<ClickSessionEntity> {
}
