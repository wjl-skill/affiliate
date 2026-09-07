package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.TenantEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户数据访问层 Mapper (Tenant Mapper)
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {
}
