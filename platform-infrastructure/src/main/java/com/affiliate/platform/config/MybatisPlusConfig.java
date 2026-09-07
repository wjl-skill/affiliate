package com.affiliate.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 核心自动装配与插件配置类 (MyBatis-Plus Configuration for PostgreSQL)
 * <p>
 * 1. 扫描 `com.affiliate.platform.mapper` 包下所有继承自 `BaseMapper` 的 Mapper 接口；
 * 2. 装配 PostgreSQL 数据库方言的分页拦截器（PaginationInnerInterceptor）与乐观锁拦截器；
 * 3. 装配防全表更新与删除拦截器（BlockAttackInnerInterceptor），防止误全量更新生产数据；
 * 4. 在开启关系数据库时自动装配生效。
 */
@Configuration
@MapperScan("com.affiliate.platform.mapper")
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true", matchIfMissing = true)
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 综合拦截器链（支持 PostgreSQL 物理分页、并发乐观锁控制与防全表操作）
     *
     * @return 拦截器链 Bean
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. PostgreSQL 物理分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(1000L); // 限制单次最大查询 1000 条，防 OOM 内存溢出
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 2. 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 3. 防全表更新与全表删除插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}

