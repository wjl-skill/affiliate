package com.affiliate.platform.affiliate.config;

import com.affiliate.platform.affiliate.repository.AffiliateApiKeyMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** 联盟扩展专属 Mapper 扫描；按注解筛选，避免扫描遗留 JPA Repository。 */
@Configuration
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackageClasses = AffiliateApiKeyMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class AffiliateMybatisConfig {}
