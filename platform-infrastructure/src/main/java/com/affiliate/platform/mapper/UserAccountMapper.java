package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.UserAccountEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户数据访问层 Mapper (User Account Mapper)
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {
}
