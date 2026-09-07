package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.S3StorageConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * S3 存储配置数据访问层 Mapper (S3 Storage Config Mapper)
 */
@Mapper
public interface S3StorageConfigMapper extends BaseMapper<S3StorageConfigEntity> {
}
