package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.AiPromptTemplateEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结构化 Prompt 模板数据访问层 Mapper (AI Prompt Template Mapper)
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplateEntity> {
}
