package com.affiliate.platform.config;

import com.affiliate.platform.tenant.TenantContext;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * MyBatis-Plus 核心自动装配与插件配置类 (MyBatis-Plus Configuration for PostgreSQL)
 * <p>
 * 1. 扫描 `com.affiliate.platform.mapper` 包下所有继承自 `BaseMapper` 的 Mapper 接口。
 * 2. 装配 TenantLineInnerInterceptor 实现全系统 SQL 级别的强租户隔离防御：
 *    采用“全表默认隔离 + 全局公用表豁免 (GLOBAL_TABLES)”架构规范。
 * 3. 严格租户防护模式 (app.tenant.strict-mode)：生产环境杜绝静默回退 "public"，缺失上下文即时阻断。
 * 4. 装配 PostgreSQL 物理分页拦截器、并发乐观锁与防全表误操作拦截器。
 */
@Configuration
@MapperScan("com.affiliate.platform.mapper")
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true", matchIfMissing = true)
public class MybatisPlusConfig {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusConfig.class);

    /**
     * 全局公用元数据表白名单（豁免 WHERE tenant_id = ? 自动注入）
     */
    private static final Set<String> GLOBAL_TABLES = Set.of(
            "flyway_schema_history",
            "sys_menu",
            "sys_role",
            "sys_permission",
            "sys_role_menu",
            "sys_user_role",
            "sys_config",
            "ai_model_config",
            "ai_prompt_template",
            "tenant",
            "tenant_quota"
    );

    @Value("${app.tenant.strict-mode:false}")
    private boolean strictMode;

    /**
     * 注册 MyBatis-Plus 综合拦截器链（支持多租户行级隔离、PostgreSQL 物理分页、并发乐观锁与防全表操作）
     *
     * @return 拦截器链 Bean
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户行级隔离拦截器 (强制对业务表 SQL 注入 WHERE tenant_id = ?)
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenant = TenantContext.get();
                if (tenant != null && !tenant.isBlank()) {
                    return new StringValue(tenant.trim());
                }
                if (TenantContext.isSystemExecution()) {
                    return new StringValue("system");
                }
                if (strictMode) {
                    throw new IllegalStateException("[CRITICAL SECURITY] 当前操作多租户业务表缺失 TenantContext，严格安全模式下禁止默认回退！");
                }
                log.warn("[TenantLineHandler] 当前操作未绑定 TenantContext，非严格模式下已降级回退至 'public'");
                return new StringValue("public");
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (tableName == null) {
                    return true;
                }
                if (TenantContext.isSystemExecution()) {
                    return true;
                }
                String lower = tableName.toLowerCase();
                // 仅对明确的全局配置和元数据表进行豁免，其余业务数据表默认 100% 施加隔离
                return GLOBAL_TABLES.contains(lower);
            }
        }));

        // 2. PostgreSQL 物理分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(1000L); // 限制单次最大查询 1000 条，防 OOM 内存溢出
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 3. 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 4. 防全表更新与全表删除插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
