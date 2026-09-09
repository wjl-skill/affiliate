package com.affiliate.platform.affiliate.repository;

import com.affiliate.platform.affiliate.domain.ApiKeyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 联盟扩展 API Key 专属 Mapper，避免与基础平台 api_key Mapper 冲突。 */
@Mapper
public interface AffiliateApiKeyMapper extends BaseMapper<ApiKeyEntity> {
    @Select("SELECT * FROM affiliate_api_key WHERE secret_key = #{secretKey} LIMIT 1")
    ApiKeyEntity findBySecretKey(String secretKey);
}
