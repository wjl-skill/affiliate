package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.UserRoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联数据访问层 Mapper (User-Role Mapper)
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {
}
