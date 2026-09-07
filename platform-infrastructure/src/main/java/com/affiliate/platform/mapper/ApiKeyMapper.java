package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.ApiKeyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开放平台 API-Key 凭证数据访问层 Mapper (API Key Mapper)
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity> {
}
