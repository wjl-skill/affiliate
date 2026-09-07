package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.RolePermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联数据访问层 Mapper (Role-Permission Mapper)
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {
}
